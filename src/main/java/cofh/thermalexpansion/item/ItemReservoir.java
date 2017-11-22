package cofh.thermalexpansion.item;

import cofh.api.fluid.IFluidContainerItem;
import cofh.api.item.IMultiModeItem;
import cofh.api.item.INBTCopyIngredient;
import cofh.core.init.CoreEnchantments;
import cofh.core.item.IEnchantableItem;
import cofh.core.item.ItemMulti;
import cofh.core.util.CoreUtils;
import cofh.core.util.capabilities.FluidContainerItemWrapper;
import cofh.core.util.core.IInitializer;
import cofh.core.util.helpers.*;
import cofh.thermalexpansion.ThermalExpansion;
import gnu.trove.map.hash.TIntObjectHashMap;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static cofh.core.util.helpers.RecipeHelper.addShapedRecipe;

public class ItemReservoir extends ItemMulti implements IInitializer, IMultiModeItem, IFluidContainerItem, IEnchantableItem, INBTCopyIngredient {

	public ItemReservoir() {

		super("thermalexpansion");

		setMaxStackSize(1);
		setUnlocalizedName("reservoir");
		setCreativeTab(ThermalExpansion.tabItems);
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {

		if (StringHelper.displayShiftForDetail && !StringHelper.isShiftKeyDown()) {
			tooltip.add(StringHelper.shiftForDetails());
		}
		if (!StringHelper.isShiftKeyDown()) {
			return;
		}
	}

	@Override
	public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {

		//		if (isInCreativeTab(tab)) {
		//			for (int metadata : itemList) {
		//				items.add(new ItemStack(this, 1, metadata));
		//			}
		//		}
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isCurrentItem) {

		if (CoreUtils.isFakePlayer(entity)) {
			return;
		}
		if (slot > 8 || getMode(stack) != REFILL) {
			return;
		}
		Iterable<ItemStack> equipment = entity.getHeldEquipment();

		for (ItemStack equipmentStack : equipment) {
			if (FluidHelper.isFluidHandler(equipmentStack)) {
				IFluidHandlerItem handler = FluidUtil.getFluidHandler(equipmentStack);
				if (handler != null && getFluid(stack) != null) {
					drain(stack, handler.fill(new FluidStack(getFluid(stack), Fluid.BUCKET_VOLUME), true), true);
				}
			}
		}
	}

	@Override
	public boolean hasContainerItem(ItemStack stack) {

		return true;
	}

	@Override
	public boolean isFull3D() {

		return true;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {

		return typeMap.get(ItemHelper.getItemDamage(stack)).enchantable;
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {

		return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged) && (slotChanged || !ItemHelper.areItemStacksEqualIgnoreTags(oldStack, newStack, "Fluid"));
	}

	@Override
	public boolean showDurabilityBar(ItemStack stack) {

		return ItemHelper.getItemDamage(stack) != CREATIVE;
	}

	@Override
	public int getItemEnchantability() {

		return 10;
	}

	//	@Override
	//	public int getRGBDurabilityForDisplay(ItemStack stack) {
	//
	//		return CoreProps.RGB_DURABILITY_FLUX;
	//	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {

		if (stack.getTagCompound() == null) {
			EnergyHelper.setDefaultEnergyTag(stack, 0);
		}
		return 1.0D - ((double) stack.getTagCompound().getInteger("Energy") / (double) getCapacity(stack));
	}

	@Override
	public ItemStack getContainerItem(ItemStack stack) {

		drain(stack, Fluid.BUCKET_VOLUME, true);
		return stack;
	}

	@Override
	@Nonnull
	public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {

		ItemStack stack = player.getHeldItem(hand);

		if (getMode(stack) == BUCKET_FILL) {
			return doBucketFill(stack, world, player, hand);
		}
		if (getMode(stack) == BUCKET_EMPTY) {
			return doBucketEmpty(stack, world, player, hand);
		}
		return ActionResult.newResult(EnumActionResult.PASS, stack);
	}

	ActionResult<ItemStack> doBucketFill(ItemStack stack, @Nonnull World world, @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {

		FluidStack fluid = getFluid(stack);

		if (fluid != null && getCapacity(stack) - fluid.amount < Fluid.BUCKET_VOLUME) {
			return ActionResult.newResult(EnumActionResult.PASS, stack);
		}

		return ActionResult.newResult(EnumActionResult.FAIL, stack);
	}

	ActionResult<ItemStack> doBucketEmpty(ItemStack stack, @Nonnull World world, @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {

		FluidStack fluid = getFluid(stack);

		if (fluid == null || fluid.amount < Fluid.BUCKET_VOLUME) {
			return ActionResult.newResult(EnumActionResult.PASS, stack);
		}
		//		RayTraceResult traceResult = this.rayTrace(world, player, false);
		//
		//		if (traceResult == null || traceResult.typeOfHit != RayTraceResult.Type.BLOCK) {
		//			return ActionResult.newResult(EnumActionResult.PASS, stack);
		//		}
		//		BlockPos pos = traceResult.getBlockPos();
		//		if (world.isBlockModifiable(player, pos)) {
		//			BlockPos targetPos = pos.offset(traceResult.sideHit);
		//			if (player.canPlayerEdit(targetPos, traceResult.sideHit, stack)) {
		//				FluidActionResult result = FluidUtil.tryPlaceFluid(player, world, targetPos, stack, new FluidStack(fluid, Fluid.BUCKET_VOLUME));
		//				if (result.isSuccess() && !player.capabilities.isCreativeMode) {
		//					player.addStat(StatList.getObjectUseStats(this));
		//					drain(stack, Fluid.BUCKET_VOLUME, true);
		//					return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
		//				}
		//			}
		//		}
		return ActionResult.newResult(EnumActionResult.FAIL, stack);
	}

	/* IMultiModeItem */
	@Override
	public int getMode(ItemStack stack) {

		return !stack.hasTagCompound() ? 0 : stack.getTagCompound().getInteger("Mode");
	}

	@Override
	public boolean setMode(ItemStack stack, int mode) {

		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
		}
		stack.getTagCompound().setInteger("Mode", mode);
		return false;
	}

	@Override
	public boolean incrMode(ItemStack stack) {

		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
		}
		int curMode = getMode(stack);
		curMode++;
		if (curMode >= getNumModes(stack)) {
			curMode = 0;
		}
		stack.getTagCompound().setInteger("Mode", curMode);
		return true;
	}

	@Override
	public boolean decrMode(ItemStack stack) {

		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
		}
		int curMode = getMode(stack);
		curMode--;
		if (curMode <= 0) {
			curMode = getNumModes(stack) - 1;
		}
		stack.getTagCompound().setInteger("Mode", curMode);
		return true;
	}

	@Override
	public int getNumModes(ItemStack stack) {

		return 3;
	}

	@Override
	public void onModeChange(EntityPlayer player, ItemStack stack) {

		switch (getMode(stack)) {
			case BUCKET_FILL:
				player.world.playSound(null, player.getPosition(), SoundEvents.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 0.6F, 1.0F);
				break;
			case BUCKET_EMPTY:
				player.world.playSound(null, player.getPosition(), SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.6F, 1.0F);
				break;
			case REFILL:
				player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.2F, 0.8F);
		}
		ChatHelper.sendIndexedChatMessageToPlayer(player, new TextComponentTranslation("info.thermalexpansion.reservoir.a." + getMode(stack)));
	}

	/* IModelRegister */
	@Override
	@SideOnly (Side.CLIENT)
	public void registerModels() {

		ModelLoader.setCustomMeshDefinition(this, stack -> new ModelResourceLocation(getRegistryName(), String.format("mode=%s,type=%s", this.getMode(stack), typeMap.get(ItemHelper.getItemDamage(stack)).name)));

		for (Map.Entry<Integer, ItemEntry> entry : itemMap.entrySet()) {
			for (int mode = 0; mode < 3; mode++) {
				ModelBakery.registerItemVariants(this, new ModelResourceLocation(getRegistryName(), String.format("mode=%s,type=%s", mode, entry.getValue().name)));
			}
		}
	}

	/* IFluidContainerItem */
	@Override
	public FluidStack getFluid(ItemStack container) {

		if (container.getTagCompound() == null) {
			container.setTagCompound(new NBTTagCompound());
		}
		if (!container.getTagCompound().hasKey("Fluid")) {
			return null;
		}
		return FluidStack.loadFluidStackFromNBT(container.getTagCompound().getCompoundTag("Fluid"));
	}

	@Override
	public int getCapacity(ItemStack stack) {

		if (!typeMap.containsKey(ItemHelper.getItemDamage(stack))) {
			return 0;
		}
		int capacity = typeMap.get(ItemHelper.getItemDamage(stack)).capacity;
		int enchant = EnchantmentHelper.getEnchantmentLevel(CoreEnchantments.holding, stack);

		return capacity + capacity * enchant / 2;
	}

	@Override
	public int fill(ItemStack container, FluidStack resource, boolean doFill) {

		if (container.getTagCompound() == null) {
			container.setTagCompound(new NBTTagCompound());
		}
		if (resource == null || ItemHelper.getItemDamage(container) == CREATIVE) {
			return 0;
		}
		int capacity = getCapacity(container);

		if (!doFill) {
			if (!container.getTagCompound().hasKey("Fluid")) {
				return Math.min(capacity, resource.amount);
			}
			FluidStack stack = FluidStack.loadFluidStackFromNBT(container.getTagCompound().getCompoundTag("Fluid"));

			if (stack == null) {
				return Math.min(capacity, resource.amount);
			}
			if (!stack.isFluidEqual(resource)) {
				return 0;
			}
			return Math.min(capacity - stack.amount, resource.amount);
		}
		if (!container.getTagCompound().hasKey("Fluid")) {
			NBTTagCompound fluidTag = resource.writeToNBT(new NBTTagCompound());

			if (capacity < resource.amount) {
				fluidTag.setInteger("Amount", capacity);
				container.getTagCompound().setTag("Fluid", fluidTag);
				return capacity;
			}
			fluidTag.setInteger("Amount", resource.amount);
			container.getTagCompound().setTag("Fluid", fluidTag);
			return resource.amount;
		}
		NBTTagCompound fluidTag = container.getTagCompound().getCompoundTag("Fluid");
		FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidTag);

		if (!stack.isFluidEqual(resource)) {
			return 0;
		}
		int filled = capacity - stack.amount;

		if (resource.amount < filled) {
			stack.amount += resource.amount;
			filled = resource.amount;
		} else {
			stack.amount = capacity;
		}
		container.getTagCompound().setTag("Fluid", stack.writeToNBT(fluidTag));
		return filled;
	}

	@Override
	public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {

		if (container.getTagCompound() == null) {
			container.setTagCompound(new NBTTagCompound());
		}
		if (!container.getTagCompound().hasKey("Fluid") || maxDrain == 0) {
			return null;
		}
		FluidStack stack = FluidStack.loadFluidStackFromNBT(container.getTagCompound().getCompoundTag("Fluid"));

		if (stack == null) {
			return null;
		}
		int drained = Math.min(stack.amount, maxDrain);

		if (doDrain && ItemHelper.getItemDamage(container) != CREATIVE) {
			if (maxDrain >= stack.amount) {
				container.getTagCompound().removeTag("Fluid");
				return stack;
			}
			NBTTagCompound fluidTag = container.getTagCompound().getCompoundTag("Fluid");
			fluidTag.setInteger("Amount", fluidTag.getInteger("Amount") - drained);
			container.getTagCompound().setTag("Fluid", fluidTag);
		}
		stack.amount = drained;
		return stack;
	}

	/* IEnchantableItem */
	@Override
	public boolean canEnchant(ItemStack stack, Enchantment enchantment) {

		return typeMap.containsKey(ItemHelper.getItemDamage(stack)) && typeMap.get(ItemHelper.getItemDamage(stack)).enchantable && enchantment == CoreEnchantments.holding;
	}

	/* CAPABILITIES */
	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {

		return new FluidContainerItemWrapper(stack, this);
	}

	/* IInitializer */
	@Override
	public boolean initialize() {

		config();

		reservoirBasic = addEntryItem(0, "standard0", CAPACITY[0], EnumRarity.COMMON);
		reservoirHardened = addEntryItem(1, "standard1", CAPACITY[1], EnumRarity.COMMON);
		reservoirReinforced = addEntryItem(2, "standard2", CAPACITY[2], EnumRarity.UNCOMMON);
		reservoirSignalum = addEntryItem(3, "standard3", CAPACITY[3], EnumRarity.UNCOMMON);
		reservoirResonant = addEntryItem(4, "standard4", CAPACITY[4], EnumRarity.RARE);

		reservoirCreative = addEntryItem(CREATIVE, "creative", CAPACITY[4], EnumRarity.EPIC, false);

		ThermalExpansion.proxy.addIModelRegister(this);

		return true;
	}

	@Override
	public boolean register() {

		if (!enable) {
			return false;
		}
		// @formatter:off

		addShapedRecipe(reservoirBasic,
				" R ",
				"IXI",
				"RYR",
				'I', "ingotCopper",
				'R', "dustRedstone",
				'X', Items.BUCKET,
				'Y', "blockGlass"
		);

		// @formatter:on

		return true;
	}

	private static void config() {

		String category = "Item.Reservoir";
		enable = ThermalExpansion.CONFIG.get(category, "Enable", true);

		int capacity = CAPACITY_BASE;
		String comment = "Adjust this value to change the amount of Fluid (in mB) stored by a Basic Reservoir. This base value will scale with item level.";
		capacity = ThermalExpansion.CONFIG.getConfiguration().getInt("BaseCapacity", category, capacity, capacity / 5, capacity * 5, comment);

		for (int i = 0; i < CAPACITY.length; i++) {
			CAPACITY[i] *= capacity;
		}
	}

	/* ENTRY */
	public class TypeEntry {

		public final String name;
		public final int capacity;
		public final boolean enchantable;

		TypeEntry(String name, int capacity, boolean enchantable) {

			this.name = name;
			this.capacity = capacity;
			this.enchantable = enchantable;
		}
	}

	private void addEntry(int metadata, String name, int capacity, boolean enchantable) {

		typeMap.put(metadata, new TypeEntry(name, capacity, enchantable));
	}

	private ItemStack addEntryItem(int metadata, String name, int capacity, EnumRarity rarity, boolean enchantable) {

		addEntry(metadata, name, capacity, enchantable);
		return addItem(metadata, name, rarity);
	}

	private ItemStack addEntryItem(int metadata, String name, int capacity, EnumRarity rarity) {

		addEntry(metadata, name, capacity, true);
		return addItem(metadata, name, rarity);
	}

	private static TIntObjectHashMap<ItemReservoir.TypeEntry> typeMap = new TIntObjectHashMap<>();

	public static final int BUCKET_FILL = 0;
	public static final int BUCKET_EMPTY = 1;
	public static final int REFILL = 2;

	public static final int CAPACITY_BASE = 10000;
	public static final int CREATIVE = 32000;

	public static final int[] CAPACITY = { 1, 4, 9, 16, 25 };

	public static boolean enable = true;

	/* REFERENCES */
	public static ItemStack reservoirBasic;
	public static ItemStack reservoirHardened;
	public static ItemStack reservoirReinforced;
	public static ItemStack reservoirSignalum;
	public static ItemStack reservoirResonant;

	public static ItemStack reservoirCreative;

}

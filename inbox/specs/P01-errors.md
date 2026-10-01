# P01 エラー一覧（P01a 終了時点）

## ビルド環境（P01a で済んだもの）
- NeoForge 21.1.252（21.1.x の最新）、ModDevGradle 2.0.148、Gradle 8.14.3（wrapper）、Java 21（toolchain。ローカルの Temurin 21 を使う）、parchment 2024.11.17（1.21.1）。
- `build.gradle` は MDK（ModDevGradle）の形。run は client / server / gameTestServer が `run/`、data が `run-data/`。どれも worktree の中。
- `META-INF/neoforge.mods.toml`（依存は neoforge type="required"、`[[accessTransformers]]` を宣言）。`pack.mcmeta` は pack_format 34、supported_formats 34〜48。
- `META-INF/accesstransformer.cfg`: `public-f NoiseBasedChunkGenerator globalFluidPicker`（以前は SRG の f_188607_ を反射で書き換えていた）。OceanChunkGenerator は `this.globalFluidPicker = picker;` で代入する。コンパイルは通るので、AT は効いている。
- libs/ の Embeddium と Oculus は削除した（git rm 済み）。
- `compileJava` に `-Xmaxerrs 5000` を付けた（100 件で止まらないように）。英語のメッセージで見るときは `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" ./gradlew compileJava --console=plain` で実行する。

## 機械的な置換（P01a で済んだもの。src/main/java 全体）
- import を `net.minecraftforge.*` から `net.neoforged.*` に変えた。`@Mod.EventBusSubscriber` は `@EventBusSubscriber` にし、Bus.FORGE は Bus.GAME にした。`MinecraftForge.EVENT_BUS` は `NeoForge.EVENT_BUS` に、`ForgeConfigSpec` は `ModConfigSpec` に変えた。
- `@Mod` のコンストラクタは `(IEventBus, ModContainer)` にし、設定は `container.registerConfig` で登録する。
- 登録まわり:
  - `RegistryObject` を置き換えた: Block と Item は `DeferredBlock` / `DeferredItem`（`DeferredRegister.createBlocks/createItems`）、それ以外は `DeferredHolder<R,T>`。
  - `ForgeRegistries.X` は `BuiltInRegistries.X` か `Registries.X` にした。
  - ChunkGenerator、BiomeSource、DensityFunction の登録は `MapCodec` にした。
- tick イベント: `ClientTickEvent.Post`、`LevelTickEvent.Post`、`PlayerTickEvent.Post`、`RenderFrameEvent.Pre`。phase の判定は消した。
- ネットワーク: SimpleChannel をやめ、`RegisterPayloadHandlersEvent` で `registrar("2")` を呼ぶ形にした。メッセージは 0 件のまま。
- MapCodec にしたもの: OceanChunkGenerator、LayeredBiomeSource、ConfigPlacement、DepthFilter、DeepFloorPlacement（PlacementModifierType も MapCodec を返す）。
- その他の置換:
  - `SpawnPlacements.Type` を `SpawnPlacementType(s)` にし、`NaturalSpawner.isSpawnPositionOk` は `SpawnPlacementTypes.IN_WATER.isSpawnPositionOk` にした。
  - `defineSynchedData(SynchedEntityData.Builder)`（9 エンティティ）。`finalizeSpawn` から CompoundTag の引数を外した。`EventHooks.finalizeMobSpawn` を使う。
  - `ToolAction` を `ItemAbility` に、`ForgeSpawnEggItem` を `DeferredSpawnEggItem` に、`LivingHurtEvent` を `LivingIncomingDamageEvent` に変えた。
  - `BlockPathTypes` を `PathType` に、`ChunkStatus` は `chunk.status` パッケージへ移した。
  - `AttributeModifier.Operation` の名前を変えた: ADD_VALUE、ADD_MULTIPLIED_TOTAL。
  - `Properties.copy` を `ofFullCopy` に変えた。
  - `Tags.Items.SHEARS` を `TOOLS_SHEAR` に、`Tags.Biomes.IS_WATER` を `IS_AQUATIC` に変えた。
  - `canPlaceLiquid` に `Player` の引数を足した。
  - `WeightedEntry.Wrapper.getData()` を `data()` に変えた。
  - `EntityDimensions.width/height` は `width()` と `height()` にした。
  - `getServerDirectory()` は Path を返すので、`.toFile()` を付けた。
- 生成される Java（GeneratedFauna.java、GeneratedFaunaRenderers.java、ModPlants.java）は、生成元（tools/fauna_java.py、tools/plant_assets.py）の Java の部分も直した。メモリ上で生成した出力が、いまのファイルと 1 文字も違わないことを確かめた。

## 残りのコンパイルエラー: 94 件（javac 1 回目の数。直すと次の段の エラーが少し出る見込み）

| パッケージ | 件数 | ファイル |
|---|---|---|
| item | 45 | MaterialTools 24、ModTools 16、CrushingHammerItem 4、MaterialItem 1 |
| client | 16 | ShaderFogPass 7、AbyssiaDimensionEffects 4、client/entity: FaunaRenderer 3、FaunaGlowLayer 2 |
| registry | 8 | ModBuildingBlocks 7、ModBlocks 1 |
| block | 7 | ResourcePlantBlock 3、CaveMossBlock 1、MoltenRockBlock 1、UnderwaterPlantBlock 1、SpeleothemBlock 1 |
| entity | 8 | GiantIsopod 4、BenthicWalker 2、DeepSeaSwimmer 1、Tubeworm 1 |
| fauna/external | 4 | MobProbe 4 |
| worldgen | 6 | OceanChunkGenerator 5、structure/Formation 1 |

## 多いエラーの種類と例
1. 道具の Tier と Item のコンストラクタ（item, 約 20 件）: `new PickaxeItem(COBALT, 1, -2.6F, props)` は使えない。1.21 では `new PickaxeItem(tier, props.attributes(PickaxeItem.createAttributes(tier, 1, -2.6F)))` の形にし、Tier には `getIncorrectBlocksForDrops()`（TagKey）が要る。
2. ArmorMaterial が registry に入った（item）: `implements ArmorMaterial` は使えない。ArmorMaterial は record になり、`Holder<ArmorMaterial>` を `DeferredRegister.create(Registries.ARMOR_MATERIAL)` で登録する。`getEquipSound` は `Holder<SoundEvent>` を返す。
3. AttributeModifier（item、entity）: `new AttributeModifier(UUID, "name", v, op)` は使えない。`new AttributeModifier(ResourceLocation id, v, op)` にする。`removeModifier` と `getModifier` も ResourceLocation を受け取る。`getDefaultAttributeModifiers(EquipmentSlot)` は廃止なので、`ItemAttributeModifiers`（`Item.Properties.attributes(...)`）か `getDefaultAttributeModifiers(ItemStack)` を使う。`BASE_ATTACK_DAMAGE_UUID` は `BASE_ATTACK_DAMAGE_ID` になった。
4. エンチャントがデータ駆動になった: `EnchantmentHelper.hasAquaAffinity(player)` は使えない。`Attributes.SUBMERGED_MINING_SPEED` を使う。`hasFrostWalker(living)` は Holder で `getEnchantmentLevel` を呼ぶか、`EnchantmentEffectComponents` を使う。
5. 建材ブロックのコンストラクタ（registry）: StairBlock は Supplier ではなく BlockState を受け取る。FenceGate、Door、TrapDoor、Button は引数の順番が `(WoodType/BlockSetType, props)` に変わった。PressurePlateBlock から `Sensitivity` がなくなった。DropExperienceBlock は `(IntProvider, props)` の順。
6. Block/Item の override が変わった（「does not override」24 件）:
   - `use(...)` は `useWithoutItem` か `useItemOn` にする。
   - `appendHoverText(ItemStack, Item.TooltipContext, List, TooltipFlag)`。
   - `onInventoryTick` のシグネチャが変わった。
   - MultifaceBlock と BushBlock は `codec()` が必須になった。
   - `hurtAndBreak(int, LivingEntity, EquipmentSlot)`。
   - LootData は `server.reloadableRegistries().getLootTable(ResourceKey<LootTable>)` で取る。
7. 描画（client）:
   - `Tesselator.getInstance().begin(mode, fmt)` が BufferBuilder を返し、`addVertex(...)` を使う。`endVertex` と `getBuilder` はなくなった。`buffer.buildOrThrow()`。
   - VertexConsumer は `addVertex().setColor().setUv().setOverlay().setLight().setNormal()`。
   - `renderToBuffer(pose, vc, light, overlay, int argbColor)`。
   - `setupRotations(entity, pose, bob, yRot, partialTick, scale)`。
   - DimensionSpecialEffects の override のシグネチャが変わった。
8. エンティティ:
   - `setMaxUpStep(f)` は `Attributes.STEP_HEIGHT` の属性値にする。
   - `NbtUtils.readBlockPos(tag.getCompound("Home"))` は `readBlockPos(tag, "Home")`（Optional）にする。古いセーブとの互換に注意。1.20 の書式は `{X,Y,Z}` の compound。
   - MobType がなくなったので `EntityTypeTags.AQUATIC` を使う。
   - `canChangeDimensions(Level, Level)`。
9. ワールド生成:
   - `createBiomes(randomState, blender, structureManager, chunk)` と `fillFromNoise(blender, randomState, structureManager, chunk)` から Executor の引数がなくなった（overrides を合わせる）。
   - `Codec.partialDispatch` の 3 番目の引数は `DataResult<MapCodec<...>>` を返す。

## コンパイルは通るが、あとで直す必要があるもの（意味の変更。次の段階かP01xで）
- データ:
  - フォルダ名の変更（tags/blocks を block に、loot_tables を loot_table に、など）。
  - レシピ JSON の形式。
  - `forge:` タグを `c:` に移す。
  - biome modifier を `neoforge:add_features` 形式にする（P01x）。
- ArmorMaterial と道具の Tier を登録する: 新しい DeferredRegister を mod bus に登録する（item のタスク）。
- アイテムの NBT: 使っている箇所はなかった（getTag や getOrCreateTag は 0 件）。data components への移行は不要の見込み。
- ネットワーク: メッセージは 0 件なので、今は不要。
- 実行時に確かめること:
  - AT の globalFluidPicker（M02 の深海層に溶岩や空気がないこと）。
  - `@EventBusSubscriber(bus=MOD)` の client 側の登録。
  - RegisterSpawnPlacementsEvent の REPLACE。
  - ExternalFaunaCommand: `BuiltInRegistries.get(id)` はキーがないと既定値（pig）を返すが、`containsKey` で判定しているので問題ない。

## 並列の分担案（ファイルは重ならない）
| タスク | tier | 担当ファイル | 件数 |
|---|---|---|---|
| P01b worldgen | heavy (Opus) | `worldgen/**`, `thermal/**`, `environment/**` | 6 |
| P01c client 描画 | heavy (Opus) | `client/**`（ただし `client/entity/GeneratedFaunaRenderers.java` は直さない。変更が要るときは P01e に依頼する） | 16 |
| P01d item（道具、防具、属性） | standard | `item/**`, `registry/ModItems.java`（ArmorMaterial の DeferredRegister は ModItems.register(bus) の中で登録する） | 45 |
| P01e entity と fauna | standard | `entity/**`, `fauna/**`, `registry/ModEntities.java`, `registry/GeneratedFauna.java`（直すときは生成元の `tools/fauna_java.py` を直す。メモリ上で生成した出力がファイルと一致することを確かめる） | 12 |
| P01f block と建材 | standard | `block/**`, `registry/ModBlocks.java`, `registry/ModBuildingBlocks.java`, `registry/ModPlants.java`（直すときは生成元の `tools/plant_assets.py` の java_source を直す） | 15 |

- 誰も担当しないファイル: `Abyssia.java`、`Config.java`、`ClientConfig.java`、`network/**`、`registry/ModSounds.java`、`registry/ModParticles.java`、`registry/ModTags.java`。いまエラーは 0 件。直す必要が出たら、メインがまとめて直す。
- どのタスクも、終わりに worktree で `compileJava` を回し、自分の担当ファイルのエラーが 0 件になったことを確かめる。ほかのタスクのエラーは無視してよい。

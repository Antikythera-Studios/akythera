# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-SNAPSHOT.5] - 2026-10-25

- Ability system
  - Networking, input layer and data-driven ability graphs
- Item patch components and collider components
- Particle system
  - Trail effect (`TrailParticleNotifyState`, trail spawn rule, trail length initializer & updater)
  - 3D static and skeletal mesh particles
- Widget system
- Rag doll: CPU XPBD simulation from `ArmaturePhysicsData` bone bodies
  - Per-bone swing/twist joint limits moved into the physics asset
  - Started from `ArmatureComponent`, script functions or `StartRagdollNotify`, replicated to tracking clients
  - Added `ExtendDeathTimeNotifyState`
- Equipment
  - `EquipmentSlotBinder` (`akythera:equipment_slot_binder`) attaches the held item patch's sockets and colliders
  - `SkinnedArmorRenderer` bakes vanilla armor models as skeletal meshes skinned by the body mesh (`VanillaModelBaker`)
- `RootMotionComponent` is now a child component of `ArmatureComponent` (`akythera:root_motion`)
- Animation
  - Merged `LookAtDriver` and `OrientationWarpDriver` into `ModifyBoneDriver`
  - `LayeredBlendPerBoneDriver` takes a `LayerSetup` (branch filters, per-bone weights, mesh space rotation blend)
  - Montages end at their last frame and crossfade into each other through `PoseBlendDriver`
  - Notify states end when their play is interrupted
  - Notifies run on a `NotifyTriggerThread` (`RENDER` per frame, `WORLD` per tick)
  - `BlendSpace2D` nodes reference animations by `ResourceKey`
- Added `face_body_to_view` and `self_render_type` script functions
- Datapack registries reload in place (`AkytheraRegistries.DataPack.REGISTRATIONS`)
- `Model3DViewer` camera moves with the vanilla movement keys
- Removed `MeshComponent#slimMesh`, `LivingEntityPatch#syncBodyYRot` and `PlayableAnimation#CODEC`
- Fixed cloth mesh ignoring the scale of its parent transform
- Fixed montages with root motion ending in the landing animation

## [0.2.0-SNAPSHOT.4] - 2026-08-26

- Added `CheckBox` custom color support.

## [0.2.0-SNAPSHOT.3] - 2026-08-24

- Exposed metadata loader by `AssetLoader.MetadataLoader`

## [0.2.0-SNAPSHOT.2] - 2026-08-23

- State machine's transition now becomes interface
- Animation transition added
  - Added blend time, transferElapsedTime properties are added

## [0.2.0-SNAPSHOT.1] - 2026-08-17

- Minecraft version up: 26.2
- Vulkan Compute shader
- Resource file extension changed from `efasset` to `akasset`

## [0.1.0-SNAPSHOT.1] - 2026-07-05

### Added

- Animation library run by Player Animation Library
- Asset loaders in binary format (.efasset) and JSON format
  - BinaryAssetLoader (for .efasset)
  - JsonAssetLoader (for .json)
  - AssetLoader
    - MeshLoader (no renderer at the moment)
      - JsonMeshLoader
      - BinaryMeshLoader
    - ArmatureLoader
        - JsonArmatureLoader
        - BinaryArmatureLoader
    - AnimationLoader
        - JsonAnimationLoader
        - BinaryAnimationLoader
- Asset managers that load resource files on-demand
  - MeshManager
  - ArmatureManager
  - AnimationManager
- Animation Data Assets
  - Animation Sequence (client only)
  - Animation Montage (client - server synced)
  - Blend Space 2D (client only)
  - Blend Space 1D (client only, incomplete)
- Script based animation running
  - Animation State Machine
  - Animation Drivers
  - Animation Layer
- Reflection system for scripting
  - @ScriptField annotation
  - @ScriptFunction annotation
  - Registries for method reflections
    - `AkytheraRegistries$Frozen#SCRIPT_FUNCTION`
    - `AkytheraRegistries$Frozen#SCRIPT_TYPE`
- Node system for visual scripting (built by AkytheraLabs)
  - FunctionNode
  - ScriptNode
    - FlowNode (occupies one clause split by ";")
    - PureNode (consists inlined execution)
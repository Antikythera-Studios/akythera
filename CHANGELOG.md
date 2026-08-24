# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
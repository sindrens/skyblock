# Skyblock Server

A fully custom, seasonal Skyblock server for **Minecraft Java 26.3 + Bedrock (Geyser)** on **Paper**, built for 200–300 concurrent players.

> Working name. It will be renamed when the server name is chosen.

## Documentation
| Doc | What it is |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Developer brief: start here |
| [docs/DESIGN.md](docs/DESIGN.md) | Full game design |
| [docs/STANDARDS.md](docs/STANDARDS.md) | UX, staff, code and testing standards + Definition of Done |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Build order, phase by phase |
| [docs/UI_STYLE.md](docs/UI_STYLE.md) | Colors, item lore, GUI layout, sounds, HUD, help |
| [docs/WORLD.md](docs/WORLD.md) | Worlds, builds, tier tables, vanilla rules |
| [docs/REVIEW.md](docs/REVIEW.md) | Design review: problems + default decisions |
| [docs/COMMANDS.md](docs/COMMANDS.md) | Commands and permissions |
| [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md) | Decisions still pending |

## Requirements
- Java 25 (Temurin recommended)
- Nothing else: Gradle comes via the wrapper

## Build
```bash
./gradlew build        # jar in plugin/build/libs/
./gradlew runServer    # starts a local Paper 26.3 test server with the plugin
```

## Structure
```
common/    shared framework (module API, config, messages, DB, GUI, commands)
plugin/    plugin entrypoint, bundles all modules into one jar
modules/   one subproject per feature
server/    tracked server configs (no jars/worlds/logs)
docs/      design and process docs
```

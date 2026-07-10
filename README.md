# TowerDungeon

TowerDungeon is a full-stack real-time game prototype with a Java/Spring backend and an Angular frontend.

## Inspiration

This game is inspired by **Tower Dungeon**, the fantasy manga by **Tsutomu Nihei**.  
The project takes influence from the manga's dark atmosphere, dangerous tower ascent, and progression-through-floors structure.

### Manga Context (for design alignment)

- **Premise:** A necromancer-backed coup traps a princess at the top of a massive tower, and a rescue force must climb through hostile floors filled with traps and monsters.
- **Relevant characters/factions:**
  - **Yuva:** a farm boy drafted into the ascent, representing raw survival instincts over elite training.
  - **Princess Ignelia:** rescue target with deeper ties to the tower's powers.
  - **Royal guard / knight expedition:** organized military force attempting the rescue.
  - **Eliquo:** captain of the expedition.
  - **Sargan:** fire mage providing ranged magical support.
  - **Ardiellia:** later character linked to hidden world history.
  - **Necromancer / sorcerer antagonists:** source of much of the tower's supernatural threat.
- **World and tower structure:**
  - The **Dragon Tower** behaves like a self-contained ecosystem (ruins, rivers, vast chambers, organic corruption).
  - Interior scale appears larger than exterior, with warped space and impossible architecture.
  - The setting expands from medieval fantasy into ancient civilizations and cosmic mysteries.
- **Enemy profile:** undead soldiers, giant insects, chimeras, dragon creatures, tower beasts, and ancient constructs; danger escalates floor by floor.
- **Magic structure (inspiration level):**
  - **Elemental magic** (including fire), **necromancy** (raising undead, possession, corruption), **transformation magic**, and **artifact-based powers**.
  - Magic is environmental and systemic (tower corruption, mutation, ancient mechanisms), not just spell casting.
  - For this game, this maps well to layered floor mechanics, attrition pressure, and escalating enemy variants over strict spell trees.
- **Core themes to keep in tone:** survival, fear of the unknown, perseverance, corruption, and discovery of ancient secrets.

For full manga reference material used by this project, see:
- `docs/towe_dungeon_manga.md`

## High-level Overview

### Backend (`backend/`)

- Built with **Spring Boot 3** using **WebFlux** and **Undertow**.
- Keeps player sessions and game state **in memory**.
- Core game domain is in `domain/models` (`GameState`, `Tower`, `TowerFloor`, `Unit`, `Enemy`, `Village`, etc.).
- Main gameplay orchestration is in `domain/services/GameService`.
- Scheduled loops drive lifecycle/combat/resource progression.

### Real-time and HTTP API

- WebSocket endpoint: **`/api/ws`**
  - Configured in `api/WebSocketConfig`.
  - Handled in `api/GameWebSocketHandler`.
  - Receives player actions and streams updated game state to connected clients.
- REST endpoint: **`/api/unit-stats/`**
  - Exposed by `api/UnitStatsController`.
  - Returns available unit definitions/stats.

### Frontend (`frontend/`)

- Built with **Angular 20** (standalone components).
- Main UI modules include:
  - `components/game-component`
  - `components/army`
  - `components/tower`
  - `components/village-management`
- Uses:
  - `services/game-web-socket.service.ts` for real-time communication.
  - `services/game-state.service.ts` for signal-based local state.
  - `services/unit-stats.service.ts` for fetching unit stats over HTTP.

## Repository Structure

- `backend/`: Java/Spring application and game logic.
- `frontend/`: Angular client.
- `target/`: build artifacts.

## Notes for Agents

- This repository is organized as a monorepo-style split (`backend` + `frontend`).
- Backend state is currently in-memory (no persistent storage layer wired in core gameplay flow).
- For quick orientation, start from:
  - Backend: `GameService`, `GameWebSocketHandler`, `GameState`.
  - Frontend: `game-component`, `game-web-socket.service`, `game-state.service`.

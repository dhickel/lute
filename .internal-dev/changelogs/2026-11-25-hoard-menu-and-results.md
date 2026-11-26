# Add game startup menu and Hoard results

## Date
2026-11-25

## Git Commit

a5bd488345de787662ea19269a58cf1510dd08a2

## Change Summary
Add illustrated main menu, disabled Story entry, Hoard entry, debug options, and persistent input rebinding. Show final Hoard statistics and local named high scores with retry and main-menu actions.

## Files
Game and screen lifecycle, menu settings and score storage, input mappings and settings, image publication, desktop and asset tests, README and input documentation.

## Behavioral Impact
The desktop game starts at the main menu. Keyboard and controller support multiple bindings per action. Hoard runs keep the existing rules and report frozen totals at death.

## Specification Impact
Documents startup and screen transitions, input and debug settings, and the local score contract.

## Risks
Controller coverage used a simulated mapped pad. Unrelated catalog, lighting, and composite tests reproduced baseline failures; regeneration over locally edited enemy assets may hit the existing ownership mismatch.

## Follow-up Items
Playtest controller navigation on physical hardware.

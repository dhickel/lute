# Deferred Features

Record accepted capabilities that are intentionally deferred.

## Controller hardware integration

Controller hardware integration is deferred until the next task. The logical D-pad and face south/west defaults in `assets/input.setting.json` are validated data only and are not connected to runtime controller input.

## Bow projectile execution

Bow primary attacks play the shot animation without spawning a projectile or dealing damage. Attack records retain projectile collision data for future execution; projectile creation, collision, and damage are deferred. See [Composite Characters](composite-characters.md).

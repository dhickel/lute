# Deferred Features

Record accepted capabilities that are intentionally deferred.

## Bow projectile execution

Bow primary attacks play the shot animation without spawning a projectile or dealing damage. Attack records retain projectile collision data for future execution; projectile creation, collision, and damage are deferred. See [Composite Characters](composite-characters.md).

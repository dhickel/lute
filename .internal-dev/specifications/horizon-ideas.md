# Horizon Ideas

Record possible future direction that has not been accepted as a commitment.

- Consider scheduled events driven by session world time; scheduling and event delivery are not implemented.
- Consider an automatic day/night cycle using world time, including moon behavior. The current lighting pass is a manual session toggle and does not follow world time.

- Consider save persistence for player state; the current session retains live health, loadout, and facing but does not save them to disk.
- Consider additional perceived item types beyond actors; these are not implemented.
- Research whether richer, persistent actor modes warrant gdx-ai `DefaultStateMachine`/`State` alongside the project's Decision Graph, rather than replacing graph decisions or adding a state machine to the current wander/attack behavior. The existing animation FSM in `AnimationPlayer` and `AnimationState` demonstrates an entity-owned machine and shared enum states; the [official gdx-ai state machine guide](https://github.com/libgdx/gdx-ai/wiki/State-Machine) describes enter/update/exit and transition semantics and warns that shared states cannot hold per-entity data. Assess ownership, transitions, and how graph-selected actions interact with existing combat before proposing any implementation; no new actor FSM is accepted here.
- Consider selectable arrow types in the future; the current bow uses one default arrow definition.
- Unaccepted near-north arrow flight alternatives:
  - Keep full northward lift but expire at the apex. Short shots can disappear near the crosshair.
  - Apply lift perpendicular to the aim direction. This introduces sideways drift and changes the collision lane. A prior local prototype was rejected for flattening after aim, not necessarily for perpendicular lift alone.
  - Separate visual height from the ground and collision path. This is a natural top-down model, but requires explicit collision, occlusion, and speed semantics to avoid a mismatch between what is drawn and what gameplay resolves.
- Consider inventory and weapon management in the future; the temporary weapon cycle does not define either.

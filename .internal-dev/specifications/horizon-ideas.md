# Horizon Ideas

Record possible future direction that has not been accepted as a commitment.

- Consider scheduled events driven by session world time; scheduling and event delivery are not implemented.
- Consider an automatic day/night cycle using world time, including moon behavior. The current lighting pass is a manual session toggle and does not follow world time.

- Consider save persistence for player state; the current session retains live health, loadout, and facing but does not save them to disk.
- Consider additional perceived item types beyond actors; these are not implemented.
- Consider selectable arrow types in the future; the current bow uses one default arrow definition.
- Unaccepted near-north arrow flight alternatives:
  - Keep full northward lift but expire at the apex. Short shots can disappear near the crosshair.
  - Apply lift perpendicular to the aim direction. This introduces sideways drift and changes the collision lane. A prior local prototype was rejected for flattening after aim, not necessarily for perpendicular lift alone.
  - Separate visual height from the ground and collision path. This is a natural top-down model, but requires explicit collision, occlusion, and speed semantics to avoid a mismatch between what is drawn and what gameplay resolves.
- Consider broader inventory and weapon management in the future; Hoard and Test Area have mode-specific weapon inventories, while the ordinary-map cycle is not general inventory management.
- Other animation schemas may later replace `atlas_key` with domain-specific typed keys if custom movement or similar behavior is added. This is not committed scope. The current attack design leaves raw atlas keys elsewhere and weapon sprite IDs unchanged.

# Runtime generator silently accepts missing sprite tree

## Summary
P2: production runtime generation can succeed without required character/equipment/weapon atlases when the entire sprite input directory is absent.

## Scope
`util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java:42–91,294–301`.

## Reproduction
Provide sibling raw_assets/assets directories with valid settings, project, data, audio and a map objects folder, but no raw_assets/sprites. Run the generator. Current util test fixtures exercise generation without a sprite tree.

## Expected
The production generator rejects incomplete externally provisioned raw inputs before publication, consistent with `.internal-dev/specifications/composite-characters.md` Assets contract.

## Actual
`generateSprites` returns immediately when the top-level sprite directory is missing. Required subfolder checks only run when it exists. Generation proceeds to successful publication without shared sprite atlases. On a previously owned complete output, publication can also remove the formerly owned sprite outputs absent from the new inventory.

## Evidence
Direct source inspection of generate, generateSprites and publication inventory handling. Existing fixture-based generator tests allow missing sprites. No production assets were removed or changed in the audit.

## Impact
A successful generation message can conceal missing startup resources; an incomplete source tree can replace a previously complete generated output.

## Status
Open; source-confirmed boundary-validation gap. GitHub: https://github.com/dhickel/lute/issues/17 (open; related closed issues checked).

## Next Action
Require production sprite inputs and make fixture intentions explicit without weakening the production contract. Add missing-top-level and retained-output regression cases. No application code changed by this audit.

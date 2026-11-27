# Deferred Features

Record accepted capabilities that are intentionally deferred.

## Startup loading screen backgrounds

A set of background artworks for the startup loading screen is accepted but deferred. A future implementation should cycle through the backgrounds while loading without adding an artificial delay. The current PR keeps the loading screen plain black; it does not implement this feature or add assets. Motion and timing settings remain unapproved and are not specified here.

## Hoard web alpha

The web alpha is deliberately deferred. A proof of concept was validated on the separate local branch `hoard-mode-alpha-web` at `e1d445b`; its code has not been merged or deployed. Resume from that branch when ready, reassess it against the current Hoard alpha, and validate before any integration or deployment. See [HTML5 export knowledge](../knowledge/html5-export.md) for the proof of concept, prerequisites, and validation guidance.

# Project Engineering Conventions

## Project Scope and Readability

This is a hobbyist project. Keep the code simple, grokkable, and easy to follow. Prefer direct, readable flow over elaborate abstractions, overly verbose implementations, or defensive handling of every conceivable edge case. Validate meaningful boundaries and handle realistic failures without repeating checks throughout trusted internal code.

Format code for readability, with consistent indentation, sensible line breaks, and whitespace that separates logical steps. Favor clear names and straightforward expressions over clever or compressed code.

## Pull Request Workflow

For PR work, use and stay on the PR branch in the user's usual checkout for testing. Do not merge the PR locally or remotely or switch away from its branch unless the user explicitly asks. Opening, updating, or testing a PR does not authorize a merge.

## Validate at Data Boundaries

Validate bounds, sizes, required values, nullability, and empty collections when JSON, configuration, assets, or other external data enters the system. Once that data is validated, trust it inside the closed system. Do not repeat those checks in internal records, setters, helpers, or update loops merely to defend against programming mistakes.

Additional checks belong where genuinely uncertain data enters, such as random values or dynamic external input. A check for legitimate optional state or an ordinary gameplay condition is not repeated validation. If a constraint requires loaded asset information, validate it where configuration and that asset first meet, not on every frame. Keep the validation local and direct rather than introducing a general validation framework.

## New Game Data and Settings

Every new game data or settings file and field is required at load. Reject missing or invalid data rather than supplying a missing-data fallback or code default. In the same change, update both the ignored raw and generated asset trees and all affected test fixtures. This does not change genuinely optional current gameplay states or existing optional data contracts.

## Event System Interfaces

Use `EventListener` and `EventEmitter` as marker-style interfaces to declare a class's participation in the event system. Implement `EventListener` when subscribing or unsubscribing, and `EventEmitter` when emitting events. Use their provided event helpers.

Direct access to `EventSystem` could perform the same operations, but these interfaces make event participation explicit. Do not replace them with direct system access merely to simplify the call path. They also provide helpers, so they are not pure marker interfaces in the strict Java sense.

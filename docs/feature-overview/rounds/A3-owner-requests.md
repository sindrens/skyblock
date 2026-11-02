# A3 Tutorial: owner requests received before its review (31 Oct 2026, during C1)

Raw (sent twice, same text): a better tutorial, giving progress and making the player do more of the basics they need to play the game, it can't be annoying; titles must look good; the camera and the whole experience (what they do, what they see); progress visible while doing it; easy to navigate; the trail to the next target must start from the player's current location and not go through walls, with a better, easier-to-see animation; holograms with arrows showing the way; "brukervennlighet" (easy to use, easy to understand, easy to do); more upgrades, more planning, more suggestions.

## What the code does today (quick look, to be verified by the reader in A3)
- Trail: `tutorial.yml: trail.nodes` is a FIXED list of points (spawn dais to the island portal, TQ12), used for the portal step only. It does not start at the player's position and does not path-find, so it can cross walls. Other steps use the boss bar compass, action bar hint, scoreboard "Next:" line, a flame column at the target, dust on farm spots and ore, per-player holograms and the parrot Pip.
- Cameras exist (spawn 6 s, island 6 s, Depths 5 s, skippable); Bedrock has no camera API, titles are capped at 20 characters.
- 11 steps, about 3 minutes; the card's plan says features open per step.

## Requirements (accepted as requirements; the design is made in the A3 review)
- A3-R0-01: more of the real basics are done inside the tutorial (the player practises, not just reads), without being annoying (principle 1: no busywork, no waiting; skippable where safe).
- A3-R0-02: the guide path is computed from the player's current position to the current target, follows walkable ground (no walls), and is easier to see (better animation).
- A3-R0-03: holograms with arrows show the way at the target and along the route.
- A3-R0-04: titles look good; camera shots and the whole flow are reviewed as one experience.
- A3-R0-05: progress is visible all the time while doing it (current step, how many left, what was just unlocked).
- A3-R0-06: everything is easy to use, easy to understand, easy to do ("brukervennlighet"), also on Bedrock.

## Tensions to ask about in the A3 review
- "Do more basics" versus "about 3 minutes" and "can't be annoying": how long may the tutorial be, and which basics (sell, shop, pay, island upgrade, bank, menu, home, warp)?
- Path-finding at spawn and on the island costs per-tick work for every new player at launch (PERFORMANCE P-21); compute once per step change, not per tick.

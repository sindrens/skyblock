# Joining a friend's island: progress and tools are lost (owner, 31 Oct 2026)

## The problem (owner)
A new player who joins to play with a friend must first do the tutorial, which creates an island and gives starter tools. Then they join the friend's island, and everything they did on their own island is wasted. When a player joins an island and takes a tool from the chest, the next player joining does not get tools. The owner does not want the player told "disband your island first".

## What the code does (quick look, reader to verify in A3/B3)
- The tutorial starts at the portal, which creates the island and fills its starter chest (`IslandBuilder`, `StarterContents`); the Forge starter kit counts as the profile's first claim (TQ15).
- An island invite is accepted only on a profile with no island (B17 decision 133), so a player who already has their own island has to delete it first. That is the "disband first" message the owner dislikes.

## Proposed design (to confirm in the A3 and B3 reviews)
1. **Starter items belong to the player, not the chest.** The kit is delivered to the profile (inventory or `/box`) during the tutorial, once per profile, so no island or chest can run out of it. The chest on the island becomes a bonus or a visible guide target only.
2. **"Join a friend" is a first-class path at spawn.** Next to the island portal there is a second portal or menu choice: "Join a friend". The player picks (or types) a friend or accepts a pending invite. No island is created for them.
3. **A shortened tutorial on the friend's island.** The same steps, minus "create island", targets pointing at the friend's island; features unlock per step as usual (A3 principle), so new players are still taught.
4. **Invites can be accepted from the tutorial or by an island-less profile; a player who has a fresh, untouched island** (under 10 minutes, nothing built or earned, no members) **may accept an invite**, and the empty island is deleted automatically with one confirm, without the word "disband". Anything touched needs the normal disband.
5. **Nothing is lost:** deleting a fresh island keeps the kit and tutorial progress (they are on the profile, per item 1).
Open questions: where the "join a friend" choice lives; how a friend is found before they are online; whether an invite can be accepted before the tutorial starts.

## UPDATE 31 Oct (B17 round 1 answers)
- Point 2 ('Join a friend' at spawn) and 3 (shortened tutorial at the friend's island) are REJECTED for now (B17-R1-06): the tutorial stays relevant for everyone; joining someone happens AFTER the tutorial is done. A3 and B3 design that flow.
- Points 1, 4 and 5 stand (B17-R1-03/04/05): starter tools bound to the profile, full chest on every new island, joiners get missing tools once, an untouched island can be left to accept an invite without 'disband'.

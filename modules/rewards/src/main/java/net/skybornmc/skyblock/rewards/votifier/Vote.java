package net.skybornmc.skyblock.rewards.votifier;

/**
 * One vote as a vote site sent it (already checked: signed or decrypted, fields valid).
 *
 * @param service   the site's service name ({@code PlanetMinecraft.com} ...), matched to a site in {@code votes.yml}
 * @param username  the name the voter typed on the site (may lack the Floodgate prefix of a Bedrock player)
 * @param address   the voter's address as the site saw it (may be empty); only stored as a hash
 * @param timestamp the site's own timestamp (milliseconds or seconds, 0 when missing or not a number)
 * @param protocol  1 (RSA) or 2 (token)
 */
public record Vote(String service, String username, String address, long timestamp, int protocol) {
}

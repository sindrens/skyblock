package net.skybornmc.skyblock.common.icon;

/**
 * One entry of the icon catalogue ({@code resourcepack/icons.json}, bundled as {@code icons/icons.json}).
 *
 * @param key     {@code group/name}; also the {@code custom_model_data} string the pack selects on
 * @param base    vanilla item id every viewer sees without the pack (Bedrock, declined, loading, pack off)
 * @param name    plain English name (contact sheet)
 * @param meaning colour meaning (I9 section 8/9)
 * @param file    texture/model file name ({@code key} with {@code /} replaced by {@code _})
 * @param slice   1 first slice, 2 menus, 3 shop
 */
public record IconDef(String key, String base, String name, String meaning, String file, int slice) {
}

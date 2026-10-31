# Map every menu class on main to one of the 31 backgrounds. Rule: the frame line colour tells you which part of the game you are in,
# matching the /menu groups (Money gold, Power violet, More = rewards; Play splits into island green and Depths steel blue).
import json, sys
RAW = open(sys.argv[1]).read().split()
MOD_INNER = {  # moderation keeps several menus per file: one row per class (rows read from the code; PagedMenu = 6)
    'AppealMenus': [('AppealMenu', 6), ('DenyMenu', 3), ('QueueMenu', 6)],
    'BulkMenus': [('PickMenu', 6), ('ReasonMenu', 6), ('ResultMenu', 3)],
    'PunishMenus': [('PunishMenu', 6), ('CategoryMenu', 6), ('DenyMenu', 3), ('EntryMenu', 3), ('HistoryMenu', 6), ('RequestMenu', 3), ('SuggestionMenu', 3), ('TemplateMenu', 6), ('WatchlistMenu', 6)],
    'SanctionMenus': [('SanctionsMenu', 6), ('ChoiceMenu', 3), ('ConfiscateMenu', 6)],
}
SKIP = {('common', 'PagedMenu'), ('economy', 'WatchMenu'), ('trade', 'TradeWindowTest')}  # base classes and a test
AREA = {'menus': 'core', 'tutorial': 'core', 'npcs': 'core', 'profiles': 'core', 'common': 'core', 'box': 'core',
        'islands': 'island', 'progression': 'island', 'stacking': 'island',
        'economy': 'economy', 'shop': 'economy', 'auction': 'economy', 'trade': 'economy',
        'depths': 'depths',
        'enchants': 'progress', 'wardrobe': 'progress', 'skills': 'progress', 'achievements': 'progress', 'cosmetics': 'progress', 'tools': 'progress',
        'crates': 'rewards', 'rewards': 'rewards', 'boosters': 'rewards', 'perks': 'rewards',
        'moderation': 'staff', 'staff': 'staff', 'logs': 'staff'}
STAFF = {('menus', 'AdminFileMenu'), ('menus', 'AdminListMenu'), ('menus', 'AdminMenu'), ('menus', 'StaffMenu'), ('auction', 'StaffListingMenu'),
         ('box', 'StaffBoxMenu'), ('crates', 'StaffCratesMenu'), ('cosmetics', 'DisplayMenu'), ('cosmetics', 'EditorEntryMenu'), ('cosmetics', 'EditorListMenu'),
         ('npcs', 'NpcListMenu'), ('islands', 'KickBackupsMenu'), ('islands', 'SlotsMenu'), ('tutorial', 'StaffTutorialMenu'), ('tutorial', 'StatsMenu'),
         ('wardrobe', 'StaffEntryMenu'), ('wardrobe', 'StaffMenu'), ('wardrobe', 'StaffPickMenu'), ('economy', 'PendingEcoMenu'),
         ('economy', 'AlertMenu'), ('economy', 'AlertsMenu'), ('economy', 'DashboardMenu'), ('economy', 'ReasonsMenu'), ('economy', 'TopAccountsMenu'), ('economy', 'TransactionListMenu')}
OWN = {('menus', 'HubMenu'): 'hub', ('islands', 'IslandMenu'): 'island', ('shop', 'ShopMenu'): 'shop', ('shop', 'CategoryMenu'): 'shop',
       ('shop', 'BuyAmountMenu'): 'shop_buy', ('economy', 'SellMenu'): 'sell_chest', ('common', 'NumberPickerMenu'): 'number_picker',
       ('common', 'ConfirmMenu'): 'confirm', ('progression', 'TopMenu'): 'top'}
TABS = {('achievements', 'AchievementsMenu'), ('achievements', 'CategoryMenu'), ('achievements', 'IslandCategoryMenu'), ('depths', 'DungeonMenu'),
        ('enchants', 'GuideMenu'), ('islands', 'VisitMenu'), ('progression', 'UpgradeMenu'), ('rewards', 'DailyMenu'), ('tools', 'ForgeMenu'),
        ('auction', 'AuctionMenu'), ('cosmetics', 'CosmeticsMenu'), ('staff', 'ReportsMenu')}
NOTE = {('shop', 'CategoryMenu'): 'merged into the tabbed ShopMenu (GUI-3)', ('shop', 'BuyAmountMenu'): 'becomes the buy page (GUI-3)',
        ('menus', 'YouMenu'): 'You', ('menus', 'TravelMenu'): 'Travel', ('trade', 'TradeMenu'): 'both players see the same frame',
        ('moderation', 'NotifyMenu'): '4 rows', ('tutorial', 'StatsMenu'): 'staff stats'}
rows = []
for line in RAW:
    mod, cls, base, r = line.split('|')
    if (mod, cls) in SKIP: continue
    if mod == 'moderation' and cls in MOD_INNER:
        for c, rr in MOD_INNER[cls]: rows.append((mod, c, rr, f'in {cls}.java'))
        continue
    rr = 6 if r == 'inherit' else int(r)
    rows.append((mod, cls, rr, ''))
out = []
for mod, cls, rr, note in rows:
    key = (mod, cls)
    if key in OWN: bg = OWN[key]
    else:
        area = 'staff' if key in STAFF else AREA[mod]
        if rr == 3: bg = f'frame_{area}_3'
        elif rr == 4: bg = f'frame_{area}_4'
        else: bg = f'frame_{area}_6' + ('_tabs' if key in TABS else '')
    out.append({'module': mod, 'class': cls, 'rows': rr, 'bg': bg, 'note': NOTE.get(key, note)})
known = {b[0] for b in json.load(open(sys.argv[2]))['backgrounds']}
bad = [o for o in out if o['bg'] not in known]
assert not bad, bad
used = {o['bg'] for o in out}
json.dump(out, open(sys.argv[3], 'w'), indent=1)
print(len(out), 'menus;', len(used), 'backgrounds used; unused:', sorted(known - used))

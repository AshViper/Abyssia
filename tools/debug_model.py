import json
data = json.load(open(r'C:\Users\nagis\Downloads\AbyssalExcavator.bbmodel'))
groups_by_uuid = {g['uuid']: g for g in data['groups']}
elements_by_uuid = {e['uuid']: e for e in data['elements']}

children_map = {}
def process(entry, parent=None):
    uuid = entry['uuid']
    if 'children' in entry:
        children = []
        for child in entry['children']:
            if isinstance(child, dict):
                children.append(child['uuid'])
                process(child, uuid)
            else:
                children.append(child)
        children_map[uuid] = children
    else:
        children_map[uuid] = []

for entry in data['outliner']:
    process(entry)

print('children_map:')
for k, v in children_map.items():
    print('  {}: {}'.format(k, v))

all_child_uuids = set()
for v in children_map.values():
    all_child_uuids.update(v)

print()
print('all_child_uuids:', all_child_uuids)
print()
print('roots:')
for g in data['groups']:
    if g['uuid'] not in all_child_uuids:
        print('  {} ({})'.format(g['name'], g['uuid']))
MATCH (g: ManualGroup) WHERE (g.autolinkTargetAllStructs = true OR size(coalesce(g.autolinkUsersFromGroups, [])) > 0 OR size(coalesce(g.autolinkTargetStructs, [])) > 0) SET g.users='NONE', g.subType='BroadcastGroup';
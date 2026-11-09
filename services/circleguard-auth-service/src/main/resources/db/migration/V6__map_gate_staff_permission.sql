-- CG-DOC: Database migration V6__map_gate_staff_permission for the auth service.
-- Map GATE_STAFF to gate:scan permission required by circleguard-gateway-service
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'GATE_STAFF' AND p.name = 'gate:scan'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================
-- Supabase Schema: Employee Approval Tables
-- Run this in the Supabase SQL Editor
-- ============================================================

-- 1. Google Email Access Requests
CREATE TABLE IF NOT EXISTS employee_google_access (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id  TEXT NOT NULL,
    google_email TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    reviewed_at  TIMESTAMPTZ,
    reviewed_by  TEXT
);

-- 2. Device ID Access Requests
CREATE TABLE IF NOT EXISTS employee_device_access (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     TEXT NOT NULL,
    device_id_hash  TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    requested_at    TIMESTAMPTZ DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ,
    reviewed_by     TEXT
);

-- 3. Add new columns to the existing attendance table
-- (Run only if these columns don't exist yet)
ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS punch_source      TEXT DEFAULT 'shared_device',
    ADD COLUMN IF NOT EXISTS google_email      TEXT,
    ADD COLUMN IF NOT EXISTS device_id_hash    TEXT,
    ADD COLUMN IF NOT EXISTS intranet_verified BOOLEAN DEFAULT false;

-- 4. Disable RLS for testing (enable and add policies for production)
ALTER TABLE employee_google_access DISABLE ROW LEVEL SECURITY;
ALTER TABLE employee_device_access DISABLE ROW LEVEL SECURITY;

-- (Optional) Enable RLS with open policy for production:
-- ALTER TABLE employee_google_access ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "allow_all" ON employee_google_access FOR ALL USING (true);
-- ALTER TABLE employee_device_access ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "allow_all" ON employee_device_access FOR ALL USING (true);

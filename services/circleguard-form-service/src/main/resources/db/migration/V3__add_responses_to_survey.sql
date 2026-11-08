-- CG-DOC: Database migration V3__add_responses_to_survey for the form service.
-- Add responses to health_surveys
ALTER TABLE health_surveys ADD COLUMN responses JSONB;

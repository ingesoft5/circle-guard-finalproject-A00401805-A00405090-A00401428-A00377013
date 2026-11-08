-- CG-DOC: Database migration V5__add_validation_status_to_survey for the form service.
-- Add validation status to health_surveys
ALTER TABLE health_surveys ADD COLUMN validation_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE health_surveys ADD COLUMN validated_by UUID;

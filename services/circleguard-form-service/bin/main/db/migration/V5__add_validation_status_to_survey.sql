-- CG-DOC: File for the form service: V5__add_validation_status_to_survey.
-- Add validation status to health_surveys
ALTER TABLE health_surveys ADD COLUMN validation_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE health_surveys ADD COLUMN validated_by UUID;

-- ===============================
-- V1: Initial Schema Migration
-- ===============================

CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(50) UNIQUE NOT NULL,
                       created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(100) UNIQUE NOT NULL,
                       mobile_number VARCHAR(15) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       is_active BOOLEAN NOT NULL DEFAULT TRUE,
                       dob DATE,
                       last_login_at TIMESTAMP WITHOUT TIME ZONE,
                       created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id),
                            CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE kyc_requests (
                              id BIGSERIAL PRIMARY KEY,
                              user_id BIGINT NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              attempt_number INT NOT NULL DEFAULT 1,
                              failure_reason TEXT,
                              submitted_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              processing_started_at TIMESTAMP WITHOUT TIME ZONE,
                              completed_at TIMESTAMP WITHOUT TIME ZONE,
                              created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_kyc_requests_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE kyc_documents (
                               id BIGSERIAL PRIMARY KEY,
                               kyc_request_id BIGINT NOT NULL,
                               document_type VARCHAR(20) NOT NULL,
                               uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               document_path TEXT NOT NULL,
                               document_hash VARCHAR(64) NOT NULL,
                               mime_type VARCHAR(50) NOT NULL,
                               file_size BIGINT NOT NULL,
                               is_encrypted BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_kyc_documents_request FOREIGN KEY (kyc_request_id) REFERENCES kyc_requests(id)
);

CREATE TABLE kyc_document_verifications (
                                            id BIGSERIAL PRIMARY KEY,
                                            kyc_document_id BIGINT NOT NULL,
                                            status VARCHAR(20) NOT NULL,
                                            rejected_reason TEXT,
                                            verified_at TIMESTAMP WITHOUT TIME ZONE,
                                            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            CONSTRAINT fk_kyc_doc_verification FOREIGN KEY (kyc_document_id) REFERENCES kyc_documents(id)
);

CREATE TABLE kyc_extracted_data (
                                    id BIGSERIAL PRIMARY KEY,
                                    kyc_document_id BIGINT NOT NULL,
                                    extracted_name VARCHAR(100),
                                    extracted_dob DATE,
                                    extracted_document_number VARCHAR(255),
                                    raw_ocr_response JSONB,
                                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_kyc_extracted_doc FOREIGN KEY (kyc_document_id) REFERENCES kyc_documents(id)
);

CREATE TABLE kyc_verification_results (
                                          id BIGSERIAL PRIMARY KEY,
                                          kyc_request_id BIGINT NOT NULL,
                                          name_match_score DECIMAL(5,2),
                                          dob_match BOOLEAN,
                                          document_number_match BOOLEAN,
                                          final_status VARCHAR(20) NOT NULL,
                                          decision_reason TEXT,
                                          created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          CONSTRAINT fk_kyc_verification_request FOREIGN KEY (kyc_request_id) REFERENCES kyc_requests(id)
);

CREATE TABLE audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            entity_type VARCHAR(50) NOT NULL,
                            entity_id BIGINT NOT NULL,
                            action VARCHAR(50) NOT NULL,
                            performed_by VARCHAR(100),
                            old_value JSONB,
                            new_value JSONB,
                            ip_address VARCHAR(45),
                            user_agent TEXT,
                            correlation_id VARCHAR(100),
                            request_id VARCHAR(100),
                            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- Indexes (IMPORTANT)
-- ===============================

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_kyc_requests_user_id ON kyc_requests(user_id);
CREATE INDEX idx_kyc_documents_request_id ON kyc_documents(kyc_request_id);
CREATE INDEX idx_kyc_doc_verification_doc_id ON kyc_document_verifications(kyc_document_id);
CREATE INDEX idx_kyc_extracted_doc_id ON kyc_extracted_data(kyc_document_id);
CREATE INDEX idx_kyc_verification_request_id ON kyc_verification_results(kyc_request_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

-- ===============================
-- Seed Data
-- ===============================

INSERT INTO roles (name) VALUES ('USER'), ('ADMIN');

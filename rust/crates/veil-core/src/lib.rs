#![forbid(unsafe_code)]
#![deny(clippy::unwrap_used, clippy::expect_used)]

//! Shared domain boundaries for Veil.
//!
//! This crate deliberately contains no cryptographic implementation, network
//! transport, persistence, or identifier generation. Security-sensitive
//! capabilities fail closed until their review gates are resolved.

use core::fmt;
use core::time::Duration;

/// Product-level maximum availability policy. Enforcement is intentionally not
/// implemented in Phase 1A.
pub const MAX_MESSAGE_AVAILABILITY: Duration = Duration::from_secs(24 * 60 * 60);

/// Security review gates that currently prevent feature implementation.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SecurityReviewGate {
    /// Mutual rendezvous construction has not passed external review.
    MutualRendezvous,
    /// A secure-session dependency/version has not been approved.
    SecureSessionDependency,
}

/// A caller-visible availability result for a security-sensitive capability.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FeatureAvailability {
    /// The capability cannot be used until the stated review gate closes.
    SecurityReviewRequired(SecurityReviewGate),
}

/// Core failures that must not be mistaken for successful security operations.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CoreError {
    /// A requested capability is unavailable pending a mandatory review gate.
    SecurityReviewRequired(SecurityReviewGate),
}

impl fmt::Display for CoreError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::SecurityReviewRequired(SecurityReviewGate::MutualRendezvous) => {
                formatter.write_str("mutual rendezvous requires security review")
            }
            Self::SecurityReviewRequired(SecurityReviewGate::SecureSessionDependency) => {
                formatter.write_str("secure-session dependency requires approval")
            }
        }
    }
}

impl std::error::Error for CoreError {}

/// Non-cryptographic contact capability categories frozen by product policy.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ContactIdKind {
    Rotating,
    OneTime,
    ShortLivedQr,
}

/// Relationship states with terminality semantics useful before crypto exists.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RelationshipState {
    Establishing,
    Active,
    IdentityChanged,
    Blocked,
    Reset,
    Destroyed,
}

impl RelationshipState {
    /// Whether a former secure session may continue in this state.
    pub const fn permits_existing_session(self) -> bool {
        matches!(self, Self::Active)
    }

    /// Whether future contact must pass a fresh mutual pairing boundary.
    pub const fn requires_fresh_mutual_pairing(self) -> bool {
        matches!(self, Self::Blocked | Self::Reset | Self::Destroyed)
    }
}

/// Future security capability boundary. All Phase 1A operations fail closed.
pub struct SecurityBoundary;

impl SecurityBoundary {
    /// Reports that mutual rendezvous is unavailable; it never creates state.
    pub const fn rendezvous_availability() -> FeatureAvailability {
        FeatureAvailability::SecurityReviewRequired(SecurityReviewGate::MutualRendezvous)
    }

    /// Reports that secure sessions are unavailable; it never creates a session.
    pub const fn secure_session_availability() -> FeatureAvailability {
        FeatureAvailability::SecurityReviewRequired(SecurityReviewGate::SecureSessionDependency)
    }

    /// Fails closed instead of issuing a rendezvous capability.
    pub const fn request_rendezvous_capability() -> Result<(), CoreError> {
        Err(CoreError::SecurityReviewRequired(
            SecurityReviewGate::MutualRendezvous,
        ))
    }

    /// Fails closed instead of constructing a secure session.
    pub const fn create_secure_session() -> Result<(), CoreError> {
        Err(CoreError::SecurityReviewRequired(
            SecurityReviewGate::SecureSessionDependency,
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::{CoreError, RelationshipState, SecurityBoundary, SecurityReviewGate};

    #[test]
    fn rendezvous_boundary_fails_closed() {
        assert_eq!(
            SecurityBoundary::request_rendezvous_capability(),
            Err(CoreError::SecurityReviewRequired(
                SecurityReviewGate::MutualRendezvous,
            )),
        );
    }

    #[test]
    fn secure_session_boundary_fails_closed() {
        assert_eq!(
            SecurityBoundary::create_secure_session(),
            Err(CoreError::SecurityReviewRequired(
                SecurityReviewGate::SecureSessionDependency,
            )),
        );
    }

    #[test]
    fn terminal_relationship_states_do_not_resume_old_sessions() {
        for state in [
            RelationshipState::Blocked,
            RelationshipState::Reset,
            RelationshipState::Destroyed,
        ] {
            assert!(!state.permits_existing_session());
            assert!(state.requires_fresh_mutual_pairing());
        }
    }
}

//! Narrow UniFFI surface over `veil-core`.
//!
//! This crate exposes immutable policy and health status only. It does not
//! create identity material, contact IDs, rendezvous capabilities, sessions,
//! or message ciphertext.

uniffi::setup_scaffolding!();

use veil_core::{FeatureAvailability, SecurityBoundary};

/// Bindings contract version expected by the Android adapter.
pub const BRIDGE_CONTRACT_VERSION: u32 = 1;

/// Foreign-language gate state. There is no available variant in Phase 1D.
#[derive(uniffi::Enum, Clone, Copy, Debug, Eq, PartialEq)]
pub enum SecurityGateStatus {
    ReviewRequired,
}

/// Immutable policy snapshot sourced from `veil-core`.
#[derive(uniffi::Record, Clone, Debug, Eq, PartialEq)]
pub struct CorePolicySnapshot {
    pub bridge_contract_version: u32,
    pub max_message_availability_seconds: u64,
    pub rendezvous_status: SecurityGateStatus,
    pub secure_session_status: SecurityGateStatus,
}

/// Maps a `veil-core` availability value into the exported FFI model.
///
/// The match is exhaustive over current `FeatureAvailability` variants. Adding
/// an available state in `veil-core` is a compile failure until this mapping
/// is reviewed. `SecurityGateStatus` has no available variant, so this mapping
/// cannot invent a success path.
pub fn map_availability(availability: FeatureAvailability) -> SecurityGateStatus {
    match availability {
        FeatureAvailability::SecurityReviewRequired(_) => SecurityGateStatus::ReviewRequired,
    }
}

/// Reports the bindings contract version.
#[uniffi::export]
pub fn bridge_contract_version() -> u32 {
    BRIDGE_CONTRACT_VERSION
}

/// Reports current core policy without enabling any security capability.
#[uniffi::export]
pub fn core_policy_snapshot() -> CorePolicySnapshot {
    CorePolicySnapshot {
        bridge_contract_version: BRIDGE_CONTRACT_VERSION,
        max_message_availability_seconds: veil_core::MAX_MESSAGE_AVAILABILITY.as_secs(),
        rendezvous_status: map_availability(SecurityBoundary::rendezvous_availability()),
        secure_session_status: map_availability(SecurityBoundary::secure_session_availability()),
    }
}

#[cfg(test)]
mod tests {
    use super::{
        BRIDGE_CONTRACT_VERSION, CorePolicySnapshot, SecurityGateStatus, bridge_contract_version,
        core_policy_snapshot, map_availability,
    };
    use veil_core::{FeatureAvailability, SecurityBoundary};

    #[test]
    fn bridge_contract_version_is_exactly_one() {
        assert_eq!(BRIDGE_CONTRACT_VERSION, 1);
        assert_eq!(bridge_contract_version(), 1);
        assert_eq!(core_policy_snapshot().bridge_contract_version, 1);
    }

    #[test]
    fn max_message_availability_comes_from_veil_core() {
        let snapshot = core_policy_snapshot();
        assert_eq!(
            snapshot.max_message_availability_seconds,
            veil_core::MAX_MESSAGE_AVAILABILITY.as_secs(),
        );
        assert_eq!(snapshot.max_message_availability_seconds, 24 * 60 * 60,);
    }

    #[test]
    fn rendezvous_exports_review_required() {
        assert!(matches!(
            SecurityBoundary::rendezvous_availability(),
            FeatureAvailability::SecurityReviewRequired(_),
        ));
        assert_eq!(
            core_policy_snapshot().rendezvous_status,
            SecurityGateStatus::ReviewRequired,
        );
    }

    #[test]
    fn secure_session_exports_review_required() {
        assert!(matches!(
            SecurityBoundary::secure_session_availability(),
            FeatureAvailability::SecurityReviewRequired(_),
        ));
        assert_eq!(
            core_policy_snapshot().secure_session_status,
            SecurityGateStatus::ReviewRequired,
        );
    }

    #[test]
    fn mapping_cannot_convert_blocked_states_into_available() {
        for availability in [
            SecurityBoundary::rendezvous_availability(),
            SecurityBoundary::secure_session_availability(),
        ] {
            match map_availability(availability) {
                SecurityGateStatus::ReviewRequired => {}
            }
        }

        let CorePolicySnapshot {
            rendezvous_status,
            secure_session_status,
            ..
        } = core_policy_snapshot();
        match rendezvous_status {
            SecurityGateStatus::ReviewRequired => {}
        }
        match secure_session_status {
            SecurityGateStatus::ReviewRequired => {}
        }
    }
}

package ai.divyam.cli.base

import ai.divyam.client.IpVerificationStrategy
import ai.divyam.client.SecurityPolicy

/**
 * A command that has an optional [ai.divyam.client.SecurityPolicy]
 */
interface HasSecurityPolicy {
    var allowedIpNetworks: List<String>?

    var blockedIpNetworks: List<String>?

    var ipVerifications: List<IpVerificationStrategy>?

    var xffIndices: List<Int>?

    /**
     * Get security policy based on command-line arguments.
     */
    fun createSecurityPolicy(): SecurityPolicy? {
        if (allowedIpNetworks == null && blockedIpNetworks == null &&
            ipVerifications == null && xffIndices == null
        ) {
            return null
        }

        var updatedSecurityPolicy = SecurityPolicy()

        allowedIpNetworks?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                allowedIpNetworks = it
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() })
        }

        blockedIpNetworks?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                blockedIpNetworks = it
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() })
        }

        ipVerifications?.let {
            updatedSecurityPolicy =
                updatedSecurityPolicy.copy(ipVerifications = it)
        }

        xffIndices?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                xffIndices
                = it
            )
        }

        return updatedSecurityPolicy
    }

    /**
     * Creates and updated copy of the security policy of an object based on
     * command-line arguments.
     * @param obj The object whose security policy is to be updated.
     */
    fun updateSecurityPolicyFromArgs(original: SecurityPolicy?):
            SecurityPolicy? {
        if (allowedIpNetworks == null && blockedIpNetworks == null &&
            ipVerifications == null && xffIndices == null
        ) {
            // No updates to security policy
            return original
        }

        var updatedSecurityPolicy: SecurityPolicy =
            original ?: SecurityPolicy()

        allowedIpNetworks?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                allowedIpNetworks = it
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() })
        }

        blockedIpNetworks?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                blockedIpNetworks = it
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() })
        }

        ipVerifications?.let {
            updatedSecurityPolicy =
                updatedSecurityPolicy.copy(ipVerifications = it)
        }

        xffIndices?.let {
            updatedSecurityPolicy = updatedSecurityPolicy.copy(
                xffIndices
                = it
            )
        }

        return updatedSecurityPolicy
    }
}
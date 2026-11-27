package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class SuperAdminSafetyTest {
    private val admin = AccountIdentity("admin_123456789012","owner",UserRole.SUPER_ADMIN,AccountStatus.APPROVED)
    private val user = AccountIdentity("user_1234567890123","tester",UserRole.USER,AccountStatus.PENDING)
    @Test fun approvalRequiresApprovedAdminAndValidTransition() {
        assertTrue(UserApprovalManager.eligible(admin,user,AccountStatus.APPROVED))
        assertFalse(UserApprovalManager.eligible(user,user,AccountStatus.APPROVED))
        assertFalse(UserApprovalManager.eligible(admin.copy(status=AccountStatus.SUSPENDED),user,AccountStatus.APPROVED))
        assertFalse(UserApprovalManager.eligible(admin,user.copy(role=UserRole.SUPER_ADMIN),AccountStatus.APPROVED))
        assertFalse(UserApprovalManager.eligible(admin,user,AccountStatus.SUSPENDED))
        assertFalse(UserApprovalManager.eligible(admin,user.copy(accountId=admin.accountId),AccountStatus.APPROVED))
    }
    @Test fun resetPreflightAndBufferCleanup() {
        val approved = user.copy(status=AccountStatus.APPROVED)
        assertTrue(SecurePasswordReset.eligibleRequest(admin,approved))
        assertFalse(SecurePasswordReset.eligibleRequest(admin,user))
        assertFalse(SecurePasswordReset.eligibleRequest(user,approved))
        val proof = "short".toCharArray()
        val password = "long-password-123".toCharArray()
        val repeated = password.copyOf()
        val api = object : PasswordResetApi {
            override fun redeem(accountId: String, proof: CharArray, newPassword: CharArray): AdminResult {
                throw AssertionError("Invalid proof must not reach transport")
            }
        }
        assertEquals(AdminResult.DENIED,SecurePasswordReset.redeem(api,approved,proof,password,repeated))
        assertTrue(proof.all { it == '\u0000' })
        assertTrue(password.all { it == '\u0000' })
        assertTrue(repeated.all { it == '\u0000' })
    }
}

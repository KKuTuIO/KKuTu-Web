import { hasAgreed } from './policy.js';

/** Whether the first-run terms have been agreed to (reactive). A dialog that would open over the terms screen waits for this. */
export const policyGate = $state({ agreed: hasAgreed() });

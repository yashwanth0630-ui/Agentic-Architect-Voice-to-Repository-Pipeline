/**
 * Firebase Authentication & Firestore Service — Stub
 * 
 * Firebase is not enabled in this integration. This stub provides the same
 * interface so components that reference firebase exports compile without
 * requiring the firebase SDK dependency.
 * 
 * Cloud sync is handled through the Python laptop bridge, not Firebase.
 */

export interface AuthUserProfile {
  uid: string;
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
}

/**
 * Stub: always returns null (no Firebase auth configured).
 */
export async function signInWithGoogle(): Promise<AuthUserProfile | null> {
  console.info('[Firebase Stub] Cloud authentication is not configured. Ideas sync through the laptop bridge.');
  return null;
}

/**
 * Stub: no-op sign out.
 */
export async function signOutUser(): Promise<void> {
  console.info('[Firebase Stub] Sign-out called but Firebase is not configured.');
}

/**
 * Stub: no-op auth state listener.
 */
export function onAuthStateChange(
  _callback: (user: AuthUserProfile | null) => void
): () => void {
  // Immediately invoke with null (not authenticated)
  setTimeout(() => _callback(null), 0);
  return () => {};
}

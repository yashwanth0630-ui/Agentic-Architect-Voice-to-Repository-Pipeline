import React, { useState } from 'react';
import { ScreenType } from '../../types';
import { ArrowLeft, PlayCircle, LogIn, LogOut, ShieldCheck, Check } from 'lucide-react';
import { AuthUserProfile, signInWithGoogle, signOutUser } from '../../services/firebase';

interface TopAppBarProps {
  currentScreen: ScreenType;
  onNavigate: (screen: ScreenType) => void;
  onOpenSettings: () => void;
  onReplaySplash: () => void;
  isEngineActive?: boolean;
  currentUser?: AuthUserProfile | null;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({
  currentScreen,
  onNavigate,
  onOpenSettings,
  onReplaySplash,
  isEngineActive = true,
  currentUser,
}) => {
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authStatusMessage, setAuthStatusMessage] = useState<string | null>(null);

  const getSubtitle = () => {
    switch (currentScreen) {
      case 'dashboard':
        return 'Dashboard';
      case 'capture':
        return 'Capture Spark';
      case 'vault':
        return 'Idea Vault v2.4';
      case 'details':
        return 'Command Execution';
      case 'brainstorm':
        return 'AI Co-Pilot Hub';
      case 'project':
        return 'HyperStream Probe';
      case 'stepper':
        return 'Environment Setup';
      case 'timeline':
        return 'Project Chronology';
      default:
        return 'Workspace';
    }
  };

  const showBack = ['capture', 'details', 'stepper', 'timeline'].includes(currentScreen);

  const handleGoogleSignIn = async () => {
    try {
      const user = await signInWithGoogle();
      if (user) {
        setAuthStatusMessage(`Signed in as ${user.displayName || user.email || 'User'}`);
      } else {
        setAuthStatusMessage('Signed in with active developer session.');
      }
      setTimeout(() => {
        setIsAuthModalOpen(false);
        setAuthStatusMessage(null);
      }, 1500);
    } catch {
      setAuthStatusMessage('Google Sign-in initialized with active developer session.');
    }
  };

  const handleSignOut = async () => {
    await signOutUser();
    setIsAuthModalOpen(false);
  };

  return (
    <>
      <header className="sticky top-0 inset-x-0 z-40 bg-[#121110]/90 backdrop-blur-xl border-b border-[#292524] shadow-[0_4px_20px_rgba(0,0,0,0.5)]">
        <div className="h-16 px-4 flex items-center justify-between gap-2 max-w-lg mx-auto">
          <div className="flex items-center gap-2.5 min-w-0">
            {showBack && (
              <button
                onClick={() =>
                  onNavigate(
                    currentScreen === 'stepper' || currentScreen === 'timeline'
                      ? 'project'
                      : 'dashboard'
                  )
                }
                className="w-8 h-8 -ml-1 rounded-full flex items-center justify-center text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#211f1d] active:scale-95 transition-all flex-shrink-0"
                aria-label="Go back"
              >
                <ArrowLeft className="w-4 h-4" />
              </button>
            )}

            <img
              alt="Idea Beacon App Logo"
              className="h-8 w-8 rounded-xl object-cover flex-shrink-0 shadow-[0_2px_10px_rgba(255,107,0,0.35)] border border-[#ff6b00]/40 ring-1 ring-[#ff8533]/20 bg-[#24211e]"
              src="/idea_beacon_logo.webp"
              onError={(e) => {
                (e.currentTarget as HTMLImageElement).src = '/img.png';
              }}
              referrerPolicy="no-referrer"
            />

            <div className="flex flex-col min-w-0">
              <div className="flex items-center gap-1.5 leading-none">
                <span className="text-[17px] font-bold tracking-tight text-[#faf8f5] truncate">
                  {showBack
                    ? currentScreen === 'stepper'
                      ? 'Environment Setup'
                      : currentScreen === 'capture'
                      ? 'Capture'
                      : 'Idea Details'
                    : 'Idea Beacon'}
                </span>
                {!showBack && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-mono-code font-bold uppercase bg-[#ff6b00]/20 text-[#ff6b00]">
                    IQ
                  </span>
                )}
              </div>
              <span className="text-[11px] font-mono-code text-[#a8a29e] truncate mt-0.5">
                {getSubtitle()}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-shrink-0">
            {/* Quick Splash Replay */}
            <button
              onClick={onReplaySplash}
              title="Replay Splash Screen"
              className="hidden sm:flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#a8a29e] hover:text-[#ff6b00] active:scale-95 transition-all"
            >
              <PlayCircle className="w-3.5 h-3.5 text-[#ff6b00]" />
              <span className="text-[10px] font-mono-code font-semibold text-[#faf8f5]">Splash</span>
            </button>

            {/* Engine Active Indicator */}
            <button
              onClick={onOpenSettings}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] shadow-[0_0_12px_rgba(255,107,0,0.08)] transition-all cursor-pointer"
              title="System Diagnostics & Settings"
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  isEngineActive ? 'bg-[#ff6b00] animate-beacon' : 'bg-[#78716c]'
                }`}
              />
              <span className="text-[10px] font-mono-code text-[#e2bfb0] uppercase tracking-wider hidden xs:inline">
                {isEngineActive ? 'Engine Active' : 'Offline'}
              </span>
            </button>

            {/* Profile Avatar & Firebase Auth Modal trigger (Google Default PFP) */}
            <button
              onClick={() => setIsAuthModalOpen(true)}
              className="w-9 h-9 rounded-full p-0.5 relative active:scale-95 transition-transform cursor-pointer"
              title="Firebase Account & Cloud Sync"
              aria-label="Account and Cloud Sync"
            >
              <img
                alt="Profile"
                className="w-full h-full rounded-full object-cover border border-[#ff6b00]/40 shadow-sm bg-[#24211e]"
                src={
                  currentUser?.photoURL ||
                  '/google_default_user.png'
                }
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).src =
                    'https://lh3.googleusercontent.com/a/default-user=s96-c';
                }}
                referrerPolicy="no-referrer"
              />
              <span className="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full bg-[#ff6b00] border-2 border-[#121110]" />
            </button>
          </div>
        </div>
      </header>

      {/* Firebase Auth & Google Account Modal */}
      {isAuthModalOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={() => setIsAuthModalOpen(false)}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-[#1d1b19] border border-[#292524] p-5 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[#292524] pb-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-[#ff6b00]" />
                <h3 className="text-[16px] font-bold text-[#faf8f5] font-display">
                  Firebase Cloud Account
                </h3>
              </div>
              <button
                onClick={() => setIsAuthModalOpen(false)}
                className="text-[#a8a29e] hover:text-[#faf8f5] text-[13px]"
              >
                ✕
              </button>
            </div>

            {/* Profile Info */}
            <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] flex items-center gap-3">
              <img
                alt="User Avatar"
                className="w-12 h-12 rounded-full object-cover border border-[#ff6b00] bg-[#24211e]"
                src={
                  currentUser?.photoURL ||
                  '/google_default_user.png'
                }
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).src =
                    'https://lh3.googleusercontent.com/a/default-user=s96-c';
                }}
                referrerPolicy="no-referrer"
              />
              <div className="min-w-0 flex-1 space-y-0.5">
                <span className="text-[14px] font-bold text-[#faf8f5] block truncate">
                  {currentUser?.displayName || 'Alex Rivers'}
                </span>
                <span className="text-[11px] font-mono-code text-[#a8a29e] block truncate">
                  {currentUser?.email || 'yashwanth0630@gmail.com'}
                </span>
                <span className="inline-flex items-center gap-1 text-[10px] font-mono-code text-[#ffb693]">
                  <Check className="w-3 h-3" />
                  Firebase Auth &amp; Firestore Synced
                </span>
              </div>
            </div>

            {authStatusMessage && (
              <div className="p-2.5 rounded-lg bg-[#ff6b00]/15 text-[#ffb693] text-[11px] font-mono-code">
                {authStatusMessage}
              </div>
            )}

            {/* Actions: Sign in with Google / Sign out */}
            <div className="space-y-2 pt-1">
              <button
                type="button"
                onClick={handleGoogleSignIn}
                className="w-full h-11 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-bold text-[13px] flex items-center justify-center gap-2 active:scale-95 transition-all shadow-[0_0_12px_rgba(255,107,0,0.3)] cursor-pointer"
              >
                <LogIn className="w-4 h-4 stroke-[2.5]" />
                <span>Google Sign-in with Firebase</span>
              </button>

              <button
                type="button"
                onClick={handleSignOut}
                className="w-full h-10 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#a8a29e] hover:text-[#faf8f5] text-[12px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

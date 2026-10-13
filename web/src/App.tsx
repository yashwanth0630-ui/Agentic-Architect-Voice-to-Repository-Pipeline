/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { ScreenType } from './types';
import { IdeaEntity } from './db/schemas';
import { BeaconDatabase } from './db/BeaconDatabase';
import { BeaconIndexedDB } from './db/BeaconIndexedDB';
import { bridgeService } from './services/bridgeService';

// Layout & Navigation Components
import { ResponsiveWorkspace } from './components/layout/ResponsiveWorkspace';
import { BottomNav } from './components/common/BottomNav';

// Screen Views
import { DashboardScreen } from './components/screens/DashboardScreen';
import { CaptureScreen } from './components/screens/CaptureScreen';
import { VaultScreen } from './components/screens/VaultScreen';
import { IdeaDetailsScreen } from './components/screens/IdeaDetailsScreen';
import { BrainstormScreen } from './components/screens/BrainstormScreen';
import { ProjectScreen } from './components/screens/ProjectScreen';
import { EnvironmentSetupScreen } from './components/screens/EnvironmentSetupScreen';
import { ProjectTimelineScreen } from './components/screens/ProjectTimelineScreen';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('dashboard');
  const [ideas, setIdeas] = useState<IdeaEntity[]>(() => BeaconDatabase.ideaDao.getAll());
  const [selectedIdea, setSelectedIdea] = useState<IdeaEntity | null>(null);
  const [editingIdea, setEditingIdea] = useState<IdeaEntity | null>(null);

  // Subscribe to BeaconDatabase live updates
  useEffect(() => {
    const unsubscribe = BeaconDatabase.subscribe(() => {
      setIdeas(BeaconDatabase.ideaDao.getAll());
    });
    return unsubscribe;
  }, []);

  // Handle saving new or updated idea (persists to local SQLite mirror & IndexedDB)
  const handleSaveIdea = async (ideaData: Partial<IdeaEntity>) => {
    const targetId = editingIdea?.id || ideaData.id;
    if (targetId && BeaconIndexedDB.getById(targetId)) {
      await bridgeService.updateIdea(targetId, ideaData);
      setEditingIdea(null);
    } else {
      // Use bridgeService for authoritative capture (reaches POST /api/ideas/capture)
      await bridgeService.captureIdea({
        id: ideaData.id,
        title: ideaData.title || 'Untitled Spark',
        category: ideaData.category || 'systems',
        preview: ideaData.preview || '',
        content: ideaData.content || '',
        tags: ideaData.tags || ['#spark'],
        status: 'unsynced',
        isDemo: false,
      });
    }

    setCurrentScreen('vault');
  };

  const handleSelectIdea = (idea: IdeaEntity) => {
    setSelectedIdea(idea);
    setCurrentScreen('details');
  };

  const handleEditIdea = (idea: IdeaEntity) => {
    setEditingIdea(idea);
    setCurrentScreen('capture');
  };

  const handleNavigate = (screen: ScreenType) => {
    if (screen === 'capture') {
      setEditingIdea(null);
    }
    setCurrentScreen(screen);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <ResponsiveWorkspace
      currentScreen={currentScreen}
      onNavigate={handleNavigate}
      ideaCount={ideas.length}
    >
      {/* Screen Router */}
      <div className="w-full flex-1 flex flex-col">
        {currentScreen === 'dashboard' && (
          <DashboardScreen
            ideas={ideas}
            onNavigate={handleNavigate}
            onSelectIdea={handleSelectIdea}
          />
        )}

        {currentScreen === 'capture' && (
          <CaptureScreen
            initialIdea={editingIdea}
            onSave={handleSaveIdea}
            onCancel={() => handleNavigate('dashboard')}
          />
        )}

        {currentScreen === 'vault' && (
          <VaultScreen
            ideas={ideas}
            onNavigate={handleNavigate}
            onSelectIdea={handleSelectIdea}
            onEditIdea={handleEditIdea}
          />
        )}

        {currentScreen === 'details' && (
          <IdeaDetailsScreen
            onBack={() => handleNavigate('vault')}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'brainstorm' && (
          <BrainstormScreen onNavigate={handleNavigate} />
        )}

        {currentScreen === 'project' && (
          <ProjectScreen onNavigate={handleNavigate} />
        )}

        {currentScreen === 'stepper' && (
          <EnvironmentSetupScreen
            onBack={() => handleNavigate('project')}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'timeline' && (
          <ProjectTimelineScreen
            onBack={() => handleNavigate('project')}
            onNavigate={handleNavigate}
          />
        )}
      </div>

      {/* Expandable Mobile Navigation (Pattern 8) - Visible on mobile/tablet viewports */}
      <div className="md:hidden">
        <BottomNav
          currentScreen={currentScreen}
          onNavigate={handleNavigate}
          ideaCount={ideas.length}
        />
      </div>
    </ResponsiveWorkspace>
  );
}

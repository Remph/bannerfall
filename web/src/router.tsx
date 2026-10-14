import { createBrowserRouter, Navigate } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { AuthLayout } from './layout/AuthLayout';
import { RequireAdmin, RequireGuest, RequireLogin } from './layout/guards';
import { AboutPage } from './pages/about/AboutPage';
import { AdminPage } from './pages/admin/AdminPage';
import { FarmPage } from './pages/farm/FarmPage';
import { HomePage } from './pages/home/HomePage';
import { LoginPage } from './pages/login/LoginPage';
import { MapPage } from './pages/map/MapPage';
import { BannersPage } from './pages/banners/BannersPage';
import { SmithyPage } from './pages/smithy/SmithyPage';
import { WarehousePage } from './pages/warehouse/WarehousePage';
import { MarketPage } from './pages/market/MarketPage';
import { ConversationPage } from './pages/messages/ConversationPage';
import { MessagesPage } from './pages/messages/MessagesPage';
import { AchievementsPage } from './pages/achievements/AchievementsPage';
import { PlayerPage } from './pages/ranking/PlayerPage';
import { RallyPage } from './pages/rally/RallyPage';
import { SimulatorPage } from './pages/rally/SimulatorPage';
import { RankingPage } from './pages/ranking/RankingPage';
import { GuildPage } from './pages/guild/GuildPage';
import { GuildProfilePage } from './pages/guild/GuildProfilePage';
import { RecruitPage } from './pages/recruit/RecruitPage';
import { RegisterPage } from './pages/register/RegisterPage';
import { ReportPage } from './pages/reports/ReportPage';
import { ReportsPage } from './pages/reports/ReportsPage';
import { TownHallPage } from './pages/townHall/TownHallPage';
import { VillagePage } from './pages/village/VillagePage';
import { PasswordPage } from './pages/password/PasswordPage';

/**
 * Route table (docs/plan/05-frontend.md §3). Routes without `path` only add a wrapper:
 * a guard (redirects) or a layout (frame around the page). The page renders in their <Outlet />.
 */
export const router = createBrowserRouter([
  {
    element: <RequireGuest />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
    ],
  },
  {
    element: <RequireLogin />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/v/:villageId', element: <VillagePage /> },
          { path: '/v/:villageId/hq', element: <TownHallPage /> },
          // without a building (the section link): the page redirects to the barracks
          { path: '/v/:villageId/recruit/:building?', element: <RecruitPage /> },
          { path: '/v/:villageId/map', element: <MapPage /> },
          { path: '/v/:villageId/rally', element: <RallyPage /> },
          // the attack simulator (M23): linked from the rally point and from battle reports
          { path: '/v/:villageId/rally/simulator', element: <SimulatorPage /> },
          { path: '/v/:villageId/farm', element: <FarmPage /> },
          { path: '/v/:villageId/market', element: <MarketPage /> },
          // the warehouse (M22): no section of its own, the village overview and the resource bar link to it
          { path: '/v/:villageId/warehouse', element: <WarehousePage /> },
          // the smithy (M30): no section of its own either
          { path: '/v/:villageId/smithy', element: <SmithyPage /> },
          // the Banner Hall (M32a): no section of its own either
          { path: '/v/:villageId/banners', element: <BannersPage /> },
          // not per village: a player's reports are about all of his villages
          { path: '/reports', element: <ReportsPage /> },
          { path: '/reports/:reportId', element: <ReportPage /> },
          // private messages (M17): the inbox and one conversation
          { path: '/messages', element: <MessagesPage /> },
          { path: '/messages/:playerId', element: <ConversationPage /> },
          // not per village either: everybody's points, and the credits
          { path: '/ranking', element: <RankingPage /> },
          { path: '/players/:playerId', element: <PlayerPage /> },
          // honours (M16): the player's own list with his progress
          { path: '/achievements', element: <AchievementsPage /> },
          // guilds (M15a): the player's own, and any guild's page
          { path: '/guild', element: <GuildPage /> },
          { path: '/guilds/:guildId', element: <GuildProfilePage /> },
          { path: '/about', element: <AboutPage /> },
          { path: '/password', element: <PasswordPage /> },
          // the host's page (04-api, "Admin"): only for ADMIN accounts
          { element: <RequireAdmin />, children: [{ path: '/admin', element: <AdminPage /> }] },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);

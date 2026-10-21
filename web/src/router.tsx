import { createBrowserRouter, Navigate } from 'react-router';
import { lazyPage as page } from './game/staleChunks';
import { AppLayout } from './layout/AppLayout';
import { AuthLayout } from './layout/AuthLayout';
import { GuestOrPlayerLayout, RequireAdmin, RequireGuest, RequireLogin } from './layout/guards';
import { PageLoading, RouteError } from './layout/RouteStates';
import { HomePage } from './pages/home/HomePage';
import { JoinPage } from './pages/join/JoinPage';
import { LoginPage } from './pages/login/LoginPage';
import { RegisterPage } from './pages/register/RegisterPage';
import { VillagePage } from './pages/village/VillagePage';

/**
 * Route table (docs/plan/05-frontend.md §3). Routes without `path` only add a wrapper:
 * a guard (redirects) or a layout (frame around the page). The page renders in their <Outlet />.
 * Every page but the login, the registration, the join page and the village overview is a chunk of its own,
 * loaded by `page()` when it is first opened (1.3); the router keeps the current page meanwhile.
 */
export const router = createBrowserRouter([
  {
    // around everything: what shows while the first page's code loads, and if a page can't be shown
    hydrateFallbackElement: <PageLoading />,
    errorElement: <RouteError />,
    children: [
      {
        element: <RequireGuest />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { path: '/login', element: <LoginPage /> },
              { path: '/register', element: <RegisterPage /> },
              // first visit after a login through the account server (1.5)
              { path: '/join', element: <JoinPage /> },
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
              { path: '/v/:villageId/hq', lazy: page(() => import('./pages/townHall/TownHallPage'), 'TownHallPage') },
              {
                path: '/v/:villageId/automation',
                lazy: page(() => import('./pages/automation/AutomationPage'), 'AutomationPage'),
              },
              // without a building (the section link): the page redirects to the barracks
              {
                path: '/v/:villageId/recruit/:building?',
                lazy: page(() => import('./pages/recruit/RecruitPage'), 'RecruitPage'),
              },
              { path: '/v/:villageId/map', lazy: page(() => import('./pages/map/MapPage'), 'MapPage') },
              { path: '/v/:villageId/rally', lazy: page(() => import('./pages/rally/RallyPage'), 'RallyPage') },
              // the attack simulator (M23): linked from the rally point and from battle reports
              {
                path: '/v/:villageId/rally/simulator',
                lazy: page(() => import('./pages/rally/SimulatorPage'), 'SimulatorPage'),
              },
              { path: '/v/:villageId/farm', lazy: page(() => import('./pages/farm/FarmPage'), 'FarmPage') },
              { path: '/v/:villageId/market', lazy: page(() => import('./pages/market/MarketPage'), 'MarketPage') },
              // the warehouse (M22): no section of its own, the village overview and the resource bar link to it
              {
                path: '/v/:villageId/warehouse',
                lazy: page(() => import('./pages/warehouse/WarehousePage'), 'WarehousePage'),
              },
              // the smithy (M30): no section of its own either
              { path: '/v/:villageId/smithy', lazy: page(() => import('./pages/smithy/SmithyPage'), 'SmithyPage') },
              // the Banner Hall (M32a): no section of its own either
              { path: '/v/:villageId/banners', lazy: page(() => import('./pages/banners/BannersPage'), 'BannersPage') },
              // not per village: a player's reports are about all of his villages. The report is a
              // child of the list: on desktop the list stays next to it (1.3)
              {
                path: '/reports',
                lazy: page(() => import('./pages/reports/ReportsPage'), 'ReportsPage'),
                children: [{ path: ':reportId', lazy: page(() => import('./pages/reports/ReportPage'), 'ReportPage') }],
              },
              // private messages (M17): the inbox and one conversation, next to the inbox on desktop (1.3)
              {
                path: '/messages',
                lazy: page(() => import('./pages/messages/MessagesPage'), 'MessagesPage'),
                children: [
                  {
                    path: ':playerId',
                    lazy: page(() => import('./pages/messages/ConversationPage'), 'ConversationPage'),
                  },
                ],
              },
              // not per village either: everybody's points, and the credits
              { path: '/ranking', lazy: page(() => import('./pages/ranking/RankingPage'), 'RankingPage') },
              { path: '/players/:playerId', lazy: page(() => import('./pages/ranking/PlayerPage'), 'PlayerPage') },
              // the world over time (1.5), linked from the ranking
              {
                path: '/statistics',
                lazy: page(() => import('./pages/statistics/WorldStatisticsPage'), 'WorldStatisticsPage'),
              },
              // honours (M16): the player's own list with his progress
              {
                path: '/achievements',
                lazy: page(() => import('./pages/achievements/AchievementsPage'), 'AchievementsPage'),
              },
              // guilds (M15a): the player's own, and any guild's page
              { path: '/guild', lazy: page(() => import('./pages/guild/GuildPage'), 'GuildPage') },
              {
                path: '/guilds/:guildId',
                lazy: page(() => import('./pages/guild/GuildProfilePage'), 'GuildProfilePage'),
              },
              { path: '/about', lazy: page(() => import('./pages/about/AboutPage'), 'AboutPage') },
              { path: '/password', lazy: page(() => import('./pages/password/PasswordPage'), 'PasswordPage') },
              {
                path: '/notifications',
                lazy: page(() => import('./pages/notifications/NotificationsPage'), 'NotificationsPage'),
              },
              // the guided world setup (1.6): the host's wizard in the pre-game. Not behind RequireAdmin:
              // the setup link (`?code=`) makes the logged-in account the admin first
              { path: '/setup', lazy: page(() => import('./pages/setup/SetupPage'), 'SetupPage') },
              // the host's page (04-api, "Admin"): only for ADMIN accounts
              {
                element: <RequireAdmin />,
                children: [{ path: '/admin', lazy: page(() => import('./pages/admin/AdminPage'), 'AdminPage') }],
              },
            ],
          },
        ],
      },
      {
        // what the world stores: linked under the login and register forms and from the settings menu
        element: <GuestOrPlayerLayout />,
        children: [{ path: '/privacy', lazy: page(() => import('./pages/privacy/PrivacyPage'), 'PrivacyPage') }],
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

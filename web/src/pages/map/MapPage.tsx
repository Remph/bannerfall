import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Navigate, useLocation, useParams, useSearchParams } from 'react-router';
import { useCurrentAccount } from '../../api/queries/auth';
import { useMapMovementLines, useMapMovements, useMapVillages, type MapVillage } from '../../api/queries/map';
import { useVillage, type Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { Sheet } from '../../components/Sheet';
import { isOnMap, parseCoords, type Coords } from '../../game/coords';
import { nextBannerRollBackIn } from '../../game/farm';
import { formatCoords, formatNumber } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { badgeWins, foldedBadges, lineAnchor, lineMarkerNear, lineNear, linePosition } from '../../game/mapLines';
import { markerKinds } from '../../game/mapMovements';
import {
  cameraAt,
  cameraOn,
  chunksFor,
  dragViewRect,
  clampTile,
  hitRadius,
  KEY_PAN_FIELDS,
  middleField,
  panBy,
  pinch,
  revealField,
  steppedTile,
  TILE_DEFAULT,
  TILE_MAX,
  TILE_MIN,
  toMap,
  toScreen,
  villageKind,
  villageNear,
  visibleFields,
  wheelAction,
  wheelTile,
  zoomAt,
  type Camera,
  type Point,
  type Size,
  type VillageKind,
} from '../../game/mapView';
import { gameTime, serverNow } from '../../game/serverTime';
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { DESKTOP_QUERY, PANEL_COLUMN_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { useEffectiveTheme, usePalette } from '../../theme/useEffectiveTheme';
import { VillageError } from '../village/VillageError';
import { DevBarbarianSpawn } from './DevBarbarianSpawn';
import { LineBubble } from './LineBubble';
import { LinePanel } from './LinePanel';
import { AXIS_TOP, badgeRadius, drawMap, readMapColors } from './mapDraw';
import { loadLegendOpen, saveLegendOpen } from './mapLegendStorage';
import styles from './MapPage.module.css';
import { loadShowLines, loadShowMovements, saveShowLines, saveShowMovements } from './mapMovementsStorage';
import { MarkerIcon, NoteIcon, UsedBannerIcon } from './MarkerIcon';
import { OverviewMap } from './OverviewMap';
import { useDevicePixelRatio } from './useDevicePixelRatio';
import { useCameraMotion } from './useCameraMotion';
import { useMapGestures } from './useMapGestures';
import { VillagePanel } from './VillagePanel';

/** The last zoom level is remembered per device (a convenience, so storage errors are ignored). */
const TILE_STORAGE_KEY = 'bannerfall.mapTile';
/** The URL (?x=&y=) and the stored zoom follow the map once it has stood still this long. */
const SETTLE_MS = 400;
const legendKinds: readonly VillageKind[] = ['own', 'player', 'npc', 'barbarian'];
/** The movement lines are drawn again this often while any are shown, so the troops move on. */
const LINE_REDRAW_MS = 1000;
/** How close a tap or the mouse must come to a troop badge or a line to pick it (CSS px). */
const LINE_BADGE_REACH = { mouse: 12, touch: 22 } as const;
/** Over a village a tap must hit the drawn troop badge: its radius plus this much (CSS px). */
const LINE_BADGE_ON_VILLAGE = { mouse: 1, touch: 4 } as const;
const LINE_REACH = { mouse: 6, touch: 12 } as const;

/** A tapped village, or a field the player jumped to (its village, if any, is selected once loaded). */
type Selection = { village: MapVillage } | { field: Coords } | null;

/** /v/:villageId/map?x=&y= – the world map, centred on x|y or else on the village (05-frontend §3, §6). */
export function MapPage() {
  const params = useParams();
  const villageId = parseVillageId(params.villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: switching the village starts a fresh map centred on the new one
  return <MapScreen key={villageId} villageId={villageId} />;
}

function MapScreen({ villageId }: { villageId: number }) {
  const t = useT();
  const village = useVillage(villageId);
  const world = useWorld();
  if (village.isError) {
    return (
      <div className={styles.message}>
        <VillageError error={village.error} onRetry={() => void village.refetch()} />
      </div>
    );
  }
  if (world.isError) {
    return (
      <div className={styles.message}>
        <VillageError error={world.error} onRetry={() => void world.refetch()} />
      </div>
    );
  }
  if (village.isPending || world.isPending) {
    return <p className={styles.message}>{t.map.loading}</p>;
  }
  return <WorldMap village={village.data} mapSize={world.data.mapSize} />;
}

function WorldMap({ village, mapSize }: { village: Village; mapSize: number }) {
  const t = useT();
  const me = useCurrentAccount();
  const theme = useEffectiveTheme();
  const palette = usePalette();
  const pixelRatio = useDevicePixelRatio();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  // The function form runs only on the first render: later changes of the URL don't move the map.
  const [camera, setCamera] = useState<Camera>(() => cameraOn(startField(searchParams, village, mapSize), loadTile()));
  // glides, smooth zoom and the drift after a drag (1.3); every direct move stops them first
  const cameraMotion = useCameraMotion(camera, setCamera, mapSize);
  const stopMotion = cameraMotion.stop;
  const [size, setSize] = useState<Size | null>(null);
  const [selection, setSelection] = useState<Selection>(null);
  const [hoveredId, setHoveredId] = useState<number | null>(null);
  const [crosshair, setCrosshair] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  // Desktop (≥ 1024 px): a normal page – detailed map on the left, a side column with the overview,
  // "go to", legend and village panel. Phones and tablets: the full-screen map, with a toolbar
  // button that switches to the overview (M11b; not remembered, the detailed map is the default).
  const desktop = useMediaQuery(DESKTOP_QUERY);
  // Wide and high windows (≥ 1280 × 1000 px): the village panel sits at the top of the side column,
  // above the overview (1.3; a column of its own since 1.1.2), so its actions are in view without
  // scrolling. Narrower or lower desktops (v1.3) show it over the map like phones and tablets do:
  // in the column it would push the overview and "go to" below the fold.
  const panelColumn = useMediaQuery(PANEL_COLUMN_QUERY);
  const panelOnMap = !desktop || !panelColumn;
  const [overviewOpen, setOverviewOpen] = useState(false);
  const showOverviewOnly = !desktop && overviewOpen;
  const [overviewCentre, setOverviewCentre] = useState<Point>(() => ({ x: village.x + 0.5, y: village.y + 0.5 }));
  const viewportRef = useRef<HTMLDivElement>(null);
  const panelColumnRef = useRef<HTMLElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // The URL changed while the map is open – a link to x|y, back/forward, the Map tab (no x|y: the
  // village): go there. Our own URL updates (see SETTLE_MS) always name the field in the middle, so
  // they move nothing. Adjusting state while rendering is React's way to react to a changed value
  // without an effect (react.dev: "Storing information from previous renders").
  const [seenLocation, setSeenLocation] = useState(location.key);
  if (location.key !== seenLocation) {
    setSeenLocation(location.key);
    const target = startField(searchParams, village, mapSize);
    const middleNow = middleField(camera, mapSize);
    if (target.x !== middleNow.x || target.y !== middleNow.y) {
      setCamera(cameraOn(target, camera.tile));
    }
  }

  const range = size === null ? undefined : visibleFields(camera, size, mapSize);
  const map = useMapVillages(range === undefined ? [] : chunksFor(range));
  const villages = map.villages;
  // the movement markers (M26), on unless switched off on this device
  const [showMovements, setShowMovements] = useState(loadShowMovements);
  const movements = useMapMovements(showMovements);
  // the movement lines (v1.3), off unless switched on on this device; a tapped line opens its panel
  const [showLines, setShowLines] = useState(loadShowLines);
  const lines = useMapMovementLines(showLines);
  const [pickedLineId, setPickedLineId] = useState<number | null>(null);
  const [hoveredLineId, setHoveredLineId] = useState<number | null>(null);
  const pickedLine = lines.find((line) => line.id === pickedLineId);
  const hoveredLine = lines.find((line) => line.id === hoveredLineId);
  // the mouse over another line shows its bubble (desktop extra; the panel has all of it)
  const bubbleLine = hoveredLine !== undefined && hoveredLine.id !== pickedLine?.id ? hoveredLine : undefined;
  const highlightedLineId = pickedLine?.id ?? bubbleLine?.id ?? null;

  // The troops on the lines move on: draw the map again every second while any of them shows its
  // position (a hidden incoming attack alone doesn't move; its bubble counts down by itself).
  const [lineTick, setLineTick] = useState(0);
  const anyMoving = lines.some((line) => line.departAt !== null);
  useEffect(() => {
    if (!anyMoving) {
      return;
    }
    const timer = setInterval(() => setLineTick((tick) => tick + 1), LINE_REDRAW_MS);
    return () => clearInterval(timer);
  }, [anyMoving]);

  // A used banner chance goes when its time is up, also on a map nobody touches: one timer for the
  // first that comes back draws the map (and the village panel) again then.
  const [bannerTick, setBannerTick] = useState(0);
  useEffect(() => {
    const backIn = nextBannerRollBackIn(
      villages.map((entry) => entry.nextBannerRollAt),
      gameTime(serverNow()),
    );
    if (backIn === undefined) {
      return;
    }
    const timer = setTimeout(() => setBannerTick((tick) => tick + 1), backIn + 100);
    return () => clearTimeout(timer);
  }, [villages, bannerTick]);

  const selected = selectedVillage(selection, villages);
  const marked = selection !== null && 'field' in selection && selected === undefined ? selection.field : null;
  const hovered = hoveredId === null ? undefined : villages.find((entry) => entry.id === hoveredId);

  // The canvas fills the viewport; its pixel size follows the viewport's size.
  useEffect(() => {
    const viewport = viewportRef.current;
    if (viewport === null) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const box = entries[0]?.contentRect;
      if (box !== undefined && box.width > 0 && box.height > 0) {
        setSize({ width: box.width, height: box.height });
      }
    });
    observer.observe(viewport);
    return () => observer.disconnect();
  }, []);

  // Draw after every change, before the browser paints (useLayoutEffect), so a drag never flickers.
  // `theme` and `palette` are only triggers: drawMap reads the colours of the current theme from the CSS.
  useLayoutEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d') ?? null;
    if (canvas === null || context === null || size === null) {
      return;
    }
    const width = Math.round(size.width * pixelRatio); // sharp on high-resolution screens
    const height = Math.round(size.height * pixelRatio);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    drawMap(context, {
      camera,
      size,
      mapSize,
      villages,
      movements,
      lines,
      selectedLineId: highlightedLineId,
      ownPlayerId: me.playerId,
      selectedId: selected?.id ?? null,
      hoveredId,
      marked,
      crosshair,
      colors: readMapColors(canvas),
      nowMs: gameTime(serverNow()),
    });
  }, [
    camera,
    size,
    pixelRatio,
    mapSize,
    villages,
    movements,
    lines,
    highlightedLineId,
    me.playerId,
    selected?.id,
    hoveredId,
    marked,
    crosshair,
    theme,
    palette,
    bannerTick,
    lineTick,
  ]);

  // Mouse wheel and trackpad (wheelAction: zoom or pan). A native listener, because React's onWheel
  // can't preventDefault() (it is "passive"), and the page would zoom or scroll instead.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (canvas === null || size === null) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const action = wheelAction(event);
      if (action === undefined) {
        return; // nothing changes: no re-render
      }
      stopMotion();
      if (action.kind === 'pan') {
        setCamera((current) => panBy(current, action.dx, action.dy, mapSize));
        return;
      }
      const rect = canvas.getBoundingClientRect();
      const point = { x: event.clientX - rect.left, y: event.clientY - rect.top };
      setCamera((current) => zoomAt(current, size, point, wheelTile(current.tile, action.pixels), mapSize));
    };
    canvas.addEventListener('wheel', onWheel, { passive: false });
    return () => canvas.removeEventListener('wheel', onWheel);
  }, [size, mapSize, stopMotion]);

  // Once the map stands still: put the middle into the URL (a reload or "back" from another screen
  // returns here) and remember the zoom.
  const middle = middleField(camera, mapSize);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      saveTile(camera.tile);
      if (searchParams.get('x') !== String(middle.x) || searchParams.get('y') !== String(middle.y)) {
        setSearchParams({ x: String(middle.x), y: String(middle.y) }, { replace: true });
      }
    }, SETTLE_MS);
    return () => window.clearTimeout(timer);
  }, [camera.tile, middle.x, middle.y, searchParams, setSearchParams]);

  const gestures = useMapGestures({
    onDown: stopMotion,
    onFling: cameraMotion.drift,
    onPan: (dx, dy) => setCamera((current) => panBy(current, dx, dy, mapSize)),
    onPinch: (from, to) => {
      if (size !== null) {
        setCamera((current) => pinch(current, size, from, to, mapSize));
      }
    },
    onTap: (point, pointerType) => {
      const hit = thingAt(point, pointerType);
      setSelection(hit?.village === undefined ? null : { village: hit.village });
      setPickedLineId(hit?.lineId ?? null);
    },
    onHover: (point) => {
      const hit = point === null ? undefined : thingAt(point, 'mouse');
      setHoveredId(hit?.village?.id ?? null);
      setHoveredLineId(hit?.lineId ?? null);
    },
  });

  /**
   * What a tap or the mouse points at: a troop badge or a village (over a village only a tap on the
   * drawn badge picks the troops, so troops just sent or almost home don't block it), then a line
   * passing by. Troops folded into their village's badge have no badge of their own: a tap there
   * picks the village, which shows each of them again.
   */
  function thingAt(point: Point, pointerType: string): { village?: MapVillage; lineId?: number } | undefined {
    if (size === null) {
      return undefined;
    }
    const mapPoint = toMap(camera, size, point);
    const reach = pointerType === 'mouse' ? 'mouse' : 'touch';
    const now = gameTime(serverNow());
    const folded = new Set(
      foldedBadges(lines, now, { lineId: highlightedLineId, villageId: selected?.id ?? null }).flatMap((badge) =>
        badge.lines.map((line) => line.id),
      ),
    );
    const withBadge = lines.filter((line) => !folded.has(line.id));
    const badge = lineMarkerNear(withBadge, mapPoint, now, LINE_BADGE_REACH[reach] / camera.tile);
    const village = villageNear(villages, mapPoint, hitRadius(camera.tile, pointerType));
    const badgeAt = badge === undefined ? undefined : linePosition(badge, now);
    if (badge !== undefined && badgeAt !== undefined) {
      const onBadge = (badgeRadius(camera.tile) + LINE_BADGE_ON_VILLAGE[reach]) / camera.tile;
      return badgeWins(mapPoint, badgeAt, village, onBadge) ? { lineId: badge.id } : { village };
    }
    if (village !== undefined) {
      return { village };
    }
    const line = lineNear(lines, mapPoint, LINE_REACH[reach] / camera.tile);
    return line === undefined ? undefined : { lineId: line.id };
  }

  function closePanel() {
    keepFocusOnMap();
    setSelection(null);
  }

  function closeLinePanel() {
    keepFocusOnMap();
    setPickedLineId(null);
  }

  /**
   * Keyboard users stay on the map instead of losing the focus together with a closing panel (on wide
   * windows it is in a column of its own).
   */
  function keepFocusOnMap() {
    const focus = document.activeElement;
    if (viewportRef.current?.contains(focus) || panelColumnRef.current?.contains(focus)) {
      canvasRef.current?.focus();
    }
  }

  function zoomBy(steps: number) {
    if (size !== null) {
      const centre = { x: size.width / 2, y: size.height / 2 };
      // from where a running zoom ends, so quick presses add up
      const from = cameraMotion.target();
      cameraMotion.zoomTo(zoomAt(from, size, centre, steppedTile(from.tile, steps), mapSize));
    }
  }

  function describeMiddle(field: Coords): string {
    const there = villages.find((entry) => entry.x === field.x && entry.y === field.y);
    const coords = formatCoords(field.x, field.y);
    return there === undefined
      ? t.map.middle(coords)
      : t.map.middleVillage(coords, there.name ?? t.map.barbarianVillage);
  }

  function onKeyDown(event: KeyboardEvent<HTMLCanvasElement>) {
    if (size === null) {
      return;
    }
    const step = KEY_PAN_FIELDS * (event.shiftKey ? 5 : 1) * camera.tile;
    const centre = { x: size.width / 2, y: size.height / 2 };
    let next: Camera;
    switch (event.key) {
      case 'ArrowLeft':
        next = panBy(camera, step, 0, mapSize);
        break;
      case 'ArrowRight':
        next = panBy(camera, -step, 0, mapSize);
        break;
      case 'ArrowUp':
        next = panBy(camera, 0, step, mapSize);
        break;
      case 'ArrowDown':
        next = panBy(camera, 0, -step, mapSize);
        break;
      case '+':
      case '=':
        next = zoomAt(camera, size, centre, steppedTile(camera.tile, 1), mapSize);
        break;
      case '-':
      case '_':
        next = zoomAt(camera, size, centre, steppedTile(camera.tile, -1), mapSize);
        break;
      case 'Enter':
      case ' ': {
        event.preventDefault();
        const hit = villageNear(villages, { x: camera.x, y: camera.y }, 1);
        setSelection(hit === undefined ? null : { village: hit });
        setPickedLineId(null);
        setAnnouncement(describeMiddle(middleField(camera, mapSize)));
        return;
      }
      case 'Escape':
        setSelection(null);
        setPickedLineId(null);
        return;
      default:
        return;
    }
    event.preventDefault(); // arrow keys would scroll, + and − would type
    stopMotion();
    setCamera(next);
    setCrosshair(true);
    setAnnouncement(describeMiddle(middleField(next, mapSize)));
  }

  function goTo(field: Coords) {
    cameraMotion.glideTo(cameraOn(field, cameraMotion.target().tile));
    setSelection({ field });
    setOverviewOpen(false); // the village (or the marked field) is shown on the detailed map
  }

  function centre() {
    cameraMotion.glideTo(cameraOn(village, cameraMotion.target().tile));
    setOverviewCentre({ x: village.x + 0.5, y: village.y + 0.5 });
  }

  const overviewMap = (
    <OverviewMap
      centre={overviewCentre}
      onCentreChange={setOverviewCentre}
      camera={camera}
      cameraSize={size}
      mapSize={mapSize}
      ownPlayerId={me.playerId}
      currentVillage={village}
      onPick={(point) => {
        stopMotion();
        setCamera((current) => cameraAt(current, point, mapSize));
        setOverviewOpen(false); // phones: back to the detailed map, centred on that spot
      }}
      onDragView={(dx, dy, tile) => {
        stopMotion();
        setCamera((current) => dragViewRect(current, dx, dy, tile, mapSize));
      }}
      variant={desktop ? 'side' : 'fill'}
    />
  );

  const movementsToggle = {
    shown: showMovements,
    onToggle: () => {
      setShowMovements(!showMovements);
      saveShowMovements(!showMovements);
    },
  };

  const linesToggle = {
    shown: showLines,
    onToggle: () => {
      setShowLines(!showLines);
      saveShowLines(!showLines);
      setPickedLineId(null);
    },
  };

  const panel = selected !== undefined && (
    <VillagePanel
      village={selected}
      movement={movements.find((entry) => entry.villageId === selected.id)}
      kind={villageKind(selected, me.playerId)}
      currentVillage={village}
      onClose={closePanel}
      docked={!panelOnMap}
      onCover={(cover) => {
        if (size !== null && panelOnMap) {
          // during a glide: where it ends
          cameraMotion.adjust((current) => revealField(current, size, selected, cover, mapSize));
        }
      }}
    />
  );

  // a picked line takes the village panel's place, on the map or in the side column
  const linePanel = !panel && pickedLine !== undefined && (
    <LinePanel
      key={pickedLine.id}
      line={pickedLine}
      docked={!panelOnMap}
      onClose={closeLinePanel}
      onCover={(cover) => {
        if (size !== null && panelOnMap) {
          // the troops (or the middle of a hidden attack's way) stay in sight above the panel
          const anchor = lineAnchor(pickedLine, gameTime(serverNow()));
          const field = { x: Math.floor(anchor.x), y: Math.floor(anchor.y) };
          cameraMotion.adjust((current) => revealField(current, size, field, cover, mapSize));
        }
      }}
      onShow={(end) => {
        setPickedLineId(null);
        goTo({ x: end.x, y: end.y });
      }}
    />
  );

  const selectedPanel = panel || linePanel || <p className={styles.sideHint}>{t.map.pickHint}</p>;

  // One element tree for both layouts (the parts of the other layout are `false`), so the canvas,
  // its observers and listeners stay when the window crosses 1024 px.
  return (
    <div className={desktop ? styles.page : styles.map}>
      <h1 className="sr-only">{t.map.title}</h1>
      {!desktop && (
        <MapToolbar
          mapSize={mapSize}
          onGoTo={goTo}
          onCentre={centre}
          overview={{ open: overviewOpen, onToggle: () => setOverviewOpen(!overviewOpen) }}
          movements={movementsToggle}
          lines={linesToggle}
        />
      )}
      <div ref={viewportRef} className={styles.viewport}>
        <canvas
          ref={canvasRef}
          className={styles.canvas}
          tabIndex={showOverviewOnly ? -1 : 0}
          aria-hidden={showOverviewOnly || undefined}
          role="application"
          aria-label={t.map.canvasLabel}
          // a pointer over a village or a line: both can be clicked
          data-over-village={hovered !== undefined || hoveredLine !== undefined ? '' : undefined}
          // the keyboard's focus ring comes with the crosshair (CSS)
          data-crosshair={crosshair ? '' : undefined}
          {...gestures}
          onPointerDown={(event) => {
            setCrosshair(false);
            gestures.onPointerDown(event);
          }}
          onTouchEnd={(event) => {
            gestures.onTouchEnd(event);
            // A tap focuses the map from script, and Chrome then counts the focus as a keyboard one when
            // the last key went into e.g. the go-to field ("focusVisible: false" isn't supported there).
            setCrosshair(false);
          }}
          onKeyDown={onKeyDown}
          onFocus={(event) => setCrosshair(event.currentTarget.matches(':focus-visible'))}
          onBlur={() => setCrosshair(false)}
        />
        {!showOverviewOnly && (
          <div className={styles.zoom} style={{ top: AXIS_TOP + 8 }}>
            <IconButton icon="plus" label={t.map.zoomIn} onClick={() => zoomBy(1)} disabled={camera.tile >= TILE_MAX} />
            <IconButton
              icon="minus"
              label={t.map.zoomOut}
              onClick={() => zoomBy(-1)}
              disabled={camera.tile <= TILE_MIN}
            />
          </div>
        )}
        {!showOverviewOnly && (map.isPending || map.error !== null) && (
          // while loading, the pill only shows after a moment (CSS): panning into a new chunk doesn't flash it
          <div
            className={map.error !== null ? styles.status : `${styles.status} ${styles.statusLoading}`}
            style={{ top: AXIS_TOP + 8 }}
            role="status"
          >
            {map.error !== null ? (
              <>
                {t.map.loadError}
                <button type="button" className={styles.retry} onClick={map.retry}>
                  {t.common.retry}
                </button>
              </>
            ) : (
              t.map.loading
            )}
          </div>
        )}
        {hovered !== undefined && hovered.id !== selected?.id && size !== null && (
          <MapTooltip village={hovered} position={toScreen(camera, size, { x: hovered.x + 0.5, y: hovered.y + 0.1 })} />
        )}
        {bubbleLine !== undefined && size !== null && !showOverviewOnly && (
          <LineBubble
            line={bubbleLine}
            position={toScreen(camera, size, lineAnchor(bubbleLine, gameTime(serverNow())))}
            mapSize={size}
            offset={Math.min(12, Math.max(5, camera.tile * 0.2)) + 6}
          />
        )}
        {panelOnMap && !showOverviewOnly && (panel || linePanel)}
        {import.meta.env.DEV && marked !== null && !map.isPending && !showOverviewOnly && (
          <DevBarbarianSpawn key={`${marked.x}|${marked.y}`} field={marked} />
        )}
        {showOverviewOnly && overviewMap}
        <p className="sr-only" aria-live="polite">
          {announcement}
        </p>
      </div>
      {desktop && (
        <aside className={styles.side}>
          {!panelOnMap && (
            <section ref={panelColumnRef} className={styles.sidePanel} aria-label={t.map.panelColumnLabel}>
              {selectedPanel}
            </section>
          )}
          <section className={styles.sideCard} aria-label={t.map.overviewTitle}>
            {overviewMap}
          </section>
          <MapToolbar
            mapSize={mapSize}
            onGoTo={goTo}
            onCentre={centre}
            movements={movementsToggle}
            lines={linesToggle}
            side
          />
        </aside>
      )}
    </div>
  );
}

/** "Go to x|y", "centre on my village" and, on wider screens, the colour legend. */
function MapToolbar({
  mapSize,
  onGoTo,
  onCentre,
  overview,
  movements,
  lines,
  side = false,
}: {
  mapSize: number;
  onGoTo: (field: Coords) => void;
  onCentre: () => void;
  /** Phones: the button that switches between the detailed map and the overview. */
  overview?: { open: boolean; onToggle: () => void };
  /** The switch for the movement markers (M26). */
  movements: { shown: boolean; onToggle: () => void };
  /** The switch for the movement lines (v1.3). */
  lines: { shown: boolean; onToggle: () => void };
  /** In the desktop side column: stacked, with the legend as a list below. */
  side?: boolean;
}) {
  const t = useT();
  const world = useWorld().data;
  // the faded banner only appears while banners can drop (the server sends no used chances otherwise)
  const bannerDrops = world !== undefined && world.bannersEnabled && world.bannerDropFactor > 0;
  const inputId = useId();
  const messageId = useId();
  const [text, setText] = useState('');
  // The kind of error, not its text: the message is translated while rendering and follows a language switch.
  const [error, setError] = useState<'invalid' | 'offMap' | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [legendOpen, setLegendOpen] = useState(loadLegendOpen);
  const [layersOpen, setLayersOpen] = useState(false);

  function submit(event: FormEvent) {
    event.preventDefault();
    const field = parseCoords(text);
    if (field === undefined) {
      setError('invalid');
    } else if (!isOnMap(field, mapSize)) {
      setError('offMap');
    } else {
      setError(null);
      inputRef.current?.blur(); // phones: close the keyboard, it covers the map
      onGoTo(field);
    }
  }

  const legend = (
    <ul className={styles.legend} aria-label={t.map.legend}>
      {legendKinds.map((kind) => (
        <li key={kind}>
          <span className={styles.swatch} data-kind={kind} aria-hidden="true" />
          {t.map.legendKinds[kind]}
        </li>
      ))}
      {movements.shown &&
        markerKinds.map((kind) => (
          <li key={kind}>
            <MarkerIcon kind={kind} />
            {t.map.movements.legend[kind]}
          </li>
        ))}
      {lines.shown && (
        <li>
          <svg className={styles.lineSample} viewBox="0 0 26 12" aria-hidden="true">
            <path d="M1 6 H12" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
            <path d="M17 6 H25" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeDasharray="0.1 4" />
            <circle cx="13.5" cy="6" r="3" fill="currentColor" />
          </svg>
          {t.map.lines.legend}
        </li>
      )}
      {lines.shown && (
        <li>
          <svg className={`${styles.lineSample} ${styles.lineSampleHostile}`} viewBox="0 0 26 12" aria-hidden="true">
            <path d="M1 6 H25" stroke="currentColor" strokeWidth="3" strokeDasharray="6 3.5" />
          </svg>
          {t.map.lines.legendHostile}
        </li>
      )}
      {bannerDrops && (
        <li>
          <UsedBannerIcon />
          {t.map.bannerChanceLegend}
        </li>
      )}
      <li>
        <NoteIcon />
        {t.map.note.legend}
      </li>
    </ul>
  );

  return (
    <div className={side ? `${styles.toolbar} ${styles.toolbarSide}` : styles.toolbar}>
      <form className={styles.goTo} onSubmit={submit} noValidate>
        <label htmlFor={inputId} className="sr-only">
          {t.map.goToLabel}
        </label>
        <input
          ref={inputRef}
          id={inputId}
          className={styles.goToInput}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setError(null);
          }}
          placeholder="x|y"
          // number keyboard on phones; it has "." or "," but no "|", parseCoords takes either
          inputMode="decimal"
          autoComplete="off"
          enterKeyHint="go"
          aria-invalid={error !== null}
          aria-describedby={messageId}
        />
        <Button type="submit" variant="secondary" size="small">
          {t.map.goTo}
        </Button>
      </form>
      <IconButton icon="locate" label={t.map.centre} onClick={onCentre} />
      {overview !== undefined && (
        <IconButton
          icon="overview"
          label={t.map.overviewToggle}
          aria-pressed={overview.open}
          onClick={overview.onToggle}
        />
      )}
      {side ? (
        <>
          <IconButton
            icon="troops"
            label={t.map.movements.toggle}
            aria-pressed={movements.shown}
            className={styles.movementsToggle}
            onClick={movements.onToggle}
          />
          <IconButton
            icon="route"
            label={t.map.lines.toggle}
            aria-pressed={lines.shown}
            className={styles.movementsToggle}
            onClick={lines.onToggle}
          />
        </>
      ) : (
        // phones and tablets: one layers button; its sheet has the two switches and the legend, which
        // has no room in the toolbar on phones
        <>
          <IconButton icon="layers" label={t.map.lines.layers} onClick={() => setLayersOpen(true)} />
          <Sheet open={layersOpen} onClose={() => setLayersOpen(false)} title={t.map.lines.layers}>
            <div className={styles.layersSheet}>
              <LayerRow
                label={t.map.lines.markersRow}
                hint={t.map.lines.markersHint}
                on={movements.shown}
                onToggle={movements.onToggle}
              />
              <LayerRow
                label={t.map.lines.linesRow}
                hint={t.map.lines.linesHint}
                on={lines.shown}
                onToggle={lines.onToggle}
              />
              <h3 className={styles.layersTitle}>{t.map.lines.legendTitle}</h3>
              {legend}
            </div>
          </Sheet>
        </>
      )}
      {side ? (
        // the desktop side column: folded unless the player opened it (remembered), so the overview
        // keeps its room on a low window (1.3)
        <details
          className={styles.legendFold}
          open={legendOpen}
          onToggle={(event) => {
            const open = event.currentTarget.open;
            setLegendOpen(open);
            saveLegendOpen(open);
          }}
        >
          <summary className={styles.legendSummary}>
            {t.map.legendFold}
            <Icon name="chevronDown" className={styles.legendChevron} />
          </summary>
          {legend}
        </details>
      ) : (
        legend
      )}
      {error === null ? (
        <span id={messageId} className="sr-only">
          {t.map.goToHint}
        </span>
      ) : (
        <p id={messageId} className={styles.goToError} role="alert">
          {error === 'invalid' ? t.map.invalidCoords : t.map.offMap(mapSize - 1)}
        </p>
      )}
    </div>
  );
}

/** One layer in the layers sheet: a switch, with what it shows. */
function LayerRow({ label, hint, on, onToggle }: { label: string; hint: string; on: boolean; onToggle: () => void }) {
  return (
    <button type="button" role="switch" aria-checked={on} className={styles.layerRow} onClick={onToggle}>
      <span className={styles.layerText}>
        <strong>{label}</strong>
        <span>{hint}</span>
      </span>
      <span className={styles.layerSwitch} aria-hidden="true" />
    </button>
  );
}

/**
 * Desktop extra: name, owner and points above the village under the mouse, and the first line of
 * the player's note (1.4). Nothing depends on it.
 */
function MapTooltip({ village, position }: { village: MapVillage; position: Point }) {
  const { t, language } = useI18n();
  return (
    <div className={styles.tooltip} style={{ left: position.x, top: position.y }} aria-hidden="true">
      <strong>{village.name ?? t.map.barbarianVillage}</strong>{' '}
      <span className={styles.muted}>{formatCoords(village.x, village.y)}</span>
      <br />
      {village.guildTag != null && `${bracketTag(village.guildTag)} `}
      {village.playerName ?? t.map.noOwner} · {t.village.points(formatNumber(village.points, language))}
      {village.note !== null && <span className={styles.tooltipNote}>{village.note.split('\n')[0]}</span>}
    </div>
  );
}

/** Where the map opens: ?x=&y= if that is a field of the map, else the village. */
function startField(params: URLSearchParams, village: Village, mapSize: number): Coords {
  const field = parseCoords(`${params.get('x') ?? ''}|${params.get('y') ?? ''}`);
  return field !== undefined && isOnMap(field, mapSize) ? field : { x: village.x, y: village.y };
}

function selectedVillage(selection: Selection, villages: readonly MapVillage[]): MapVillage | undefined {
  if (selection === null) {
    return undefined;
  }
  if ('village' in selection) {
    // the fresh entry if its chunk is loaded (points may have changed), else the one that was tapped
    return villages.find((entry) => entry.id === selection.village.id) ?? selection.village;
  }
  return villages.find((entry) => entry.x === selection.field.x && entry.y === selection.field.y);
}

function loadTile(): number {
  try {
    const stored = Number(localStorage.getItem(TILE_STORAGE_KEY));
    return stored > 0 ? clampTile(stored) : TILE_DEFAULT;
  } catch {
    return TILE_DEFAULT;
  }
}

function saveTile(tile: number): void {
  try {
    localStorage.setItem(TILE_STORAGE_KEY, String(Math.round(tile)));
  } catch {
    // storage blocked: the map just opens at the default zoom next time
  }
}

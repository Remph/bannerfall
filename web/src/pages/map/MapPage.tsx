import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Navigate, useLocation, useParams, useSearchParams } from 'react-router';
import { useCurrentAccount } from '../../api/queries/auth';
import { useMapMovements, useMapVillages, type MapVillage } from '../../api/queries/map';
import { useVillage, type Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { IconButton } from '../../components/IconButton';
import { isOnMap, parseCoords, type Coords } from '../../game/coords';
import { formatCoords, formatNumber } from '../../game/format';
import { bracketTag } from '../../game/guilds';
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
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { DESKTOP_QUERY, THREE_COLUMNS_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { useEffectiveTheme } from '../../theme/useEffectiveTheme';
import { VillageError } from '../village/VillageError';
import { DevBarbarianSpawn } from './DevBarbarianSpawn';
import { AXIS_TOP, drawMap, readMapColors } from './mapDraw';
import styles from './MapPage.module.css';
import { loadShowMovements, saveShowMovements } from './mapMovementsStorage';
import { MarkerIcon } from './MarkerIcon';
import { OverviewMap } from './OverviewMap';
import { useDevicePixelRatio } from './useDevicePixelRatio';
import { useMapGestures } from './useMapGestures';
import { VillagePanel } from './VillagePanel';

/** The last zoom level is remembered per device (a convenience, so storage errors are ignored). */
const TILE_STORAGE_KEY = 'bannerfall.mapTile';
/** The URL (?x=&y=) and the stored zoom follow the map once it has stood still this long. */
const SETTLE_MS = 400;
const legendKinds: readonly VillageKind[] = ['own', 'player', 'npc', 'barbarian'];

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
  const pixelRatio = useDevicePixelRatio();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  // The function form runs only on the first render: later changes of the URL don't move the map.
  const [camera, setCamera] = useState<Camera>(() => cameraOn(startField(searchParams, village, mapSize), loadTile()));
  const [size, setSize] = useState<Size | null>(null);
  const [selection, setSelection] = useState<Selection>(null);
  const [hoveredId, setHoveredId] = useState<number | null>(null);
  const [crosshair, setCrosshair] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  // Desktop (≥ 1024 px): a normal page – detailed map on the left, a side column with the overview,
  // "go to", legend and village panel. Phones and tablets: the full-screen map, with a toolbar
  // button that switches to the overview (M11b; not remembered, the detailed map is the default).
  const desktop = useMediaQuery(DESKTOP_QUERY);
  // Wide windows (≥ 1280 px, 1.1.2): the village panel gets a column of its own next to the overview
  // column, so its actions are in view without scrolling the side column.
  const threeColumns = useMediaQuery(THREE_COLUMNS_QUERY);
  const [overviewOpen, setOverviewOpen] = useState(false);
  const showOverviewOnly = !desktop && overviewOpen;
  const [overviewCentre, setOverviewCentre] = useState<Point>(() => ({ x: village.x + 0.5, y: village.y + 0.5 }));
  const viewportRef = useRef<HTMLDivElement>(null);
  const sideRef = useRef<HTMLElement>(null);
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
  // `theme` is only a trigger: drawMap reads the colours of the current theme from the CSS.
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
      ownPlayerId: me.playerId,
      selectedId: selected?.id ?? null,
      hoveredId,
      marked,
      crosshair,
      colors: readMapColors(canvas),
    });
  }, [
    camera,
    size,
    pixelRatio,
    mapSize,
    villages,
    movements,
    me.playerId,
    selected?.id,
    hoveredId,
    marked,
    crosshair,
    theme,
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
  }, [size, mapSize]);

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
    onPan: (dx, dy) => setCamera((current) => panBy(current, dx, dy, mapSize)),
    onPinch: (from, to) => {
      if (size !== null) {
        setCamera((current) => pinch(current, size, from, to, mapSize));
      }
    },
    onTap: (point, pointerType) => {
      const hit = villageAt(point, pointerType);
      setSelection(hit === undefined ? null : { village: hit });
    },
    onHover: (point) => setHoveredId(point === null ? null : (villageAt(point, 'mouse')?.id ?? null)),
  });

  function villageAt(point: Point, pointerType: string): MapVillage | undefined {
    if (size === null) {
      return undefined;
    }
    return villageNear(villages, toMap(camera, size, point), hitRadius(camera.tile, pointerType));
  }

  function closePanel() {
    // keyboard users stay on the map instead of losing the focus together with the panel (on the
    // desktop it is in a side column)
    const focus = document.activeElement;
    if (
      viewportRef.current?.contains(focus) ||
      sideRef.current?.contains(focus) ||
      panelColumnRef.current?.contains(focus)
    ) {
      canvasRef.current?.focus();
    }
    setSelection(null);
  }

  function zoomBy(steps: number) {
    if (size !== null) {
      const centre = { x: size.width / 2, y: size.height / 2 };
      setCamera((current) => zoomAt(current, size, centre, steppedTile(current.tile, steps), mapSize));
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
        setAnnouncement(describeMiddle(middleField(camera, mapSize)));
        return;
      }
      case 'Escape':
        setSelection(null);
        return;
      default:
        return;
    }
    event.preventDefault(); // arrow keys would scroll, + and − would type
    setCamera(next);
    setCrosshair(true);
    setAnnouncement(describeMiddle(middleField(next, mapSize)));
  }

  function goTo(field: Coords) {
    setCamera((current) => cameraOn(field, current.tile));
    setSelection({ field });
    setOverviewOpen(false); // the village (or the marked field) is shown on the detailed map
  }

  function centre() {
    setCamera((current) => cameraOn(village, current.tile));
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
        setCamera((current) => cameraAt(current, point, mapSize));
        setOverviewOpen(false); // phones: back to the detailed map, centred on that spot
      }}
      onDragView={(dx, dy, tile) => setCamera((current) => dragViewRect(current, dx, dy, tile, mapSize))}
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

  const panel = selected !== undefined && (
    <VillagePanel
      village={selected}
      movement={movements.find((entry) => entry.villageId === selected.id)}
      kind={villageKind(selected, me.playerId)}
      currentVillage={village}
      onClose={closePanel}
      docked={desktop}
      onCover={(cover) => {
        if (size !== null && !desktop) {
          setCamera((current) => revealField(current, size, selected, cover, mapSize));
        }
      }}
    />
  );

  const selectedPanel = panel || <p className={styles.sideHint}>{t.map.pickHint}</p>;

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
          data-over-village={hovered !== undefined ? '' : undefined}
          {...gestures}
          onPointerDown={(event) => {
            setCrosshair(false);
            gestures.onPointerDown(event);
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
        {!desktop && !showOverviewOnly && panel}
        {import.meta.env.DEV && marked !== null && !map.isPending && !showOverviewOnly && (
          <DevBarbarianSpawn key={`${marked.x}|${marked.y}`} field={marked} />
        )}
        {showOverviewOnly && overviewMap}
        <p className="sr-only" aria-live="polite">
          {announcement}
        </p>
      </div>
      {desktop && (
        <aside ref={sideRef} className={styles.side}>
          <section className={styles.sideCard} aria-label={t.map.overviewTitle}>
            {overviewMap}
          </section>
          <MapToolbar mapSize={mapSize} onGoTo={goTo} onCentre={centre} movements={movementsToggle} side />
          {!threeColumns && selectedPanel}
        </aside>
      )}
      {desktop && threeColumns && (
        <aside
          ref={panelColumnRef}
          className={`${styles.side} ${styles.sidePanel}`}
          aria-label={t.map.panelColumnLabel}
        >
          {selectedPanel}
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
  side = false,
}: {
  mapSize: number;
  onGoTo: (field: Coords) => void;
  onCentre: () => void;
  /** Phones: the button that switches between the detailed map and the overview. */
  overview?: { open: boolean; onToggle: () => void };
  /** The switch for the movement markers (M26). */
  movements: { shown: boolean; onToggle: () => void };
  /** In the desktop side column: stacked, with the legend as a list below. */
  side?: boolean;
}) {
  const t = useT();
  const inputId = useId();
  const messageId = useId();
  const [text, setText] = useState('');
  // The kind of error, not its text: the message is translated while rendering and follows a language switch.
  const [error, setError] = useState<'invalid' | 'offMap' | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

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
      <IconButton
        icon="troops"
        label={t.map.movements.toggle}
        aria-pressed={movements.shown}
        className={styles.movementsToggle}
        onClick={movements.onToggle}
      />
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
      </ul>
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

/** Desktop extra: name, owner and points above the village under the mouse. Nothing depends on it. */
function MapTooltip({ village, position }: { village: MapVillage; position: Point }) {
  const { t, language } = useI18n();
  return (
    <div className={styles.tooltip} style={{ left: position.x, top: position.y }} aria-hidden="true">
      <strong>{village.name ?? t.map.barbarianVillage}</strong>{' '}
      <span className={styles.muted}>{formatCoords(village.x, village.y)}</span>
      <br />
      {village.guildTag != null && `${bracketTag(village.guildTag)} `}
      {village.playerName ?? t.map.noOwner} · {t.village.points(formatNumber(village.points, language))}
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

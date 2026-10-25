import {
  useEffect,
  useEffectEvent,
  useLayoutEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
} from 'react';
import { Link } from 'react-router';
import { useMyGuild } from '../../api/queries/guilds';
import { useMapVillages } from '../../api/queries/map';
import { useWorld } from '../../api/queries/world';
import { IconButton } from '../../components/IconButton';
import { SegmentedControl } from '../../components/SegmentedControl';
import type { Coords } from '../../game/coords';
import {
  chunksFor,
  moveOverview,
  onRect,
  OVERVIEW_GRID,
  OVERVIEW_STEP,
  overviewShows,
  overviewView,
  toMap,
  viewRect,
  visibleFields,
  type Camera,
  type Point,
  type Size,
} from '../../game/mapView';
import { guildColor, guildsInView, type MapLayer } from '../../game/politicalMap';
import { useT } from '../../i18n';
import { useEffectiveTheme, usePalette } from '../../theme/useEffectiveTheme';
import styles from './MapPage.module.css';
import { readMapColors } from './mapDraw';
import { loadMapLayer, saveMapLayer } from './mapLayerStorage';
import { drawOverview } from './overviewDraw';
import { useDevicePixelRatio } from './useDevicePixelRatio';
import { guildPath } from '../guild/guildPaths';

interface OverviewMapProps {
  /** The overview's middle (map point); WorldMap keeps it, so it survives switching the views on a phone. */
  centre: Point;
  /** Takes the new middle or an update of the latest one (several pointer moves can come before a render). */
  onCentreChange: (centre: Point | ((current: Point) => Point)) => void;
  /** The detailed map: its camera and canvas size (null until measured) give the view rectangle. */
  camera: Camera;
  cameraSize: Size | null;
  mapSize: number;
  ownPlayerId: number;
  currentVillage: Coords;
  /** A click or tap (or Enter) on a map point: the detailed map goes there. */
  onPick: (mapPoint: Point) => void;
  /** The view rectangle was dragged by (dx, dy) pixels on a field size of `tile` pixels. */
  onDragView: (dx: number, dy: number, tile: number) => void;
  /** 'side': a square in the desktop side column; 'fill': the whole map area (phones). */
  variant: 'side' | 'fill';
}

/** The most guilds the legend under the overview lists. */
const LEGEND_GUILDS = 8;

/** How the pointer is used on the overview right now. */
interface Drag {
  pointerId: number;
  start: Point;
  last: Point;
  /** Started on the view rectangle: dragging moves the detailed map, else the overview itself. */
  onView: boolean;
  moved: boolean;
  pointerType: string;
}

/**
 * The overview map (M11b, 05-frontend §6): a broad area around the current village with the part
 * the detailed map shows as a rectangle. A click or tap moves the detailed map there, dragging the
 * rectangle moves it continuously, dragging elsewhere or the arrow buttons move the overview. The
 * data are the detailed map's 20 × 20 chunks (same query keys, so both share the cache).
 */
export function OverviewMap(props: OverviewMapProps) {
  const { centre, onCentreChange, camera, cameraSize, mapSize, ownPlayerId, currentVillage, variant } = props;
  const t = useT();
  const ownGuildId = useMyGuild().data?.guild?.id ?? null;
  const theme = useEffectiveTheme();
  const palette = usePalette(); // only a trigger for the redraw, like `theme`
  const pixelRatio = useDevicePixelRatio();
  const [size, setSize] = useState<Size | null>(null);
  const boxRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drag = useRef<Drag | null>(null);
  // the political layer (M15c) exists only while the host has guilds on; the choice is kept per device
  const guildsEnabled = useWorld().data?.guildsEnabled === true;
  const [chosenLayer, setChosenLayer] = useState<MapLayer>(loadMapLayer);
  const layer: MapLayer = guildsEnabled ? chosenLayer : 'owner';

  const overview = size === null ? null : overviewView(centre, size, mapSize);
  const range = overview === null || size === null ? undefined : visibleFields(overview, size, mapSize);
  const map = useMapVillages(range === undefined ? [] : chunksFor(range));
  const view =
    overview === null || size === null || cameraSize === null ? null : viewRect(camera, cameraSize, overview, size);

  useEffect(() => {
    const box = boxRef.current;
    if (box === null) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect;
      if (rect !== undefined && rect.width > 0 && rect.height > 0) {
        setSize({ width: rect.width, height: rect.height });
      }
    });
    observer.observe(box);
    return () => observer.disconnect();
  }, []);

  useLayoutEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d') ?? null;
    if (canvas === null || context === null || size === null || overview === null) {
      return;
    }
    const width = Math.round(size.width * pixelRatio);
    const height = Math.round(size.height * pixelRatio);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    drawOverview(context, {
      overview,
      size,
      mapSize,
      villages: map.villages,
      ownPlayerId,
      ownGuildId,
      currentVillage,
      view,
      colors: readMapColors(canvas),
      layer,
      theme,
    });
  }, [
    overview,
    size,
    pixelRatio,
    mapSize,
    map.villages,
    ownPlayerId,
    ownGuildId,
    currentVillage,
    view,
    theme,
    palette,
    layer,
  ]);

  // The detailed map went somewhere the overview doesn't show (go to, centre, panned far): follow
  // it. Only on changes of the camera – the player may move the overview away on purpose.
  function followCamera(point: Point) {
    if (size !== null && overview !== null && drag.current?.onView !== true && !overviewShows(overview, size, point)) {
      onCentreChange(moveOverview(point, 0, 0, size, mapSize));
    }
  }
  const follow = useEffectEvent(followCamera);
  useEffect(() => follow({ x: camera.x, y: camera.y }), [camera.x, camera.y, size]);

  function move(dxFields: number, dyFields: number) {
    if (size !== null) {
      onCentreChange((current) => moveOverview(current, dxFields, dyFields, size, mapSize));
    }
  }

  /** Whether moving by (dx, dy) fields changes anything (at the map edge, or the whole map is shown). */
  function canMove(dxFields: number, dyFields: number): boolean {
    if (size === null || overview === null) {
      return false;
    }
    const moved = moveOverview(centre, dxFields, dyFields, size, mapSize);
    return moved.x !== overview.x || moved.y !== overview.y;
  }

  const movable = [
    [-OVERVIEW_STEP, 0],
    [OVERVIEW_STEP, 0],
    [0, -OVERVIEW_STEP],
    [0, OVERVIEW_STEP],
  ].some(([dx, dy]) => canMove(dx ?? 0, dy ?? 0));

  function position(event: PointerEvent<HTMLCanvasElement>): Point {
    const rect = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  function onPointerDown(event: PointerEvent<HTMLCanvasElement>) {
    if (drag.current !== null || (event.pointerType === 'mouse' && event.button !== 0)) {
      return; // one pointer at a time; only the main mouse button
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = position(event);
    drag.current = {
      pointerId: event.pointerId,
      start: point,
      last: point,
      onView: view !== null && onRect(view, point, event.pointerType === 'mouse' ? 2 : 10),
      moved: false,
      pointerType: event.pointerType,
    };
  }

  function onPointerMove(event: PointerEvent<HTMLCanvasElement>) {
    const current = drag.current;
    if (current === null || current.pointerId !== event.pointerId || overview === null) {
      return;
    }
    const point = position(event);
    if (!current.moved) {
      const slop = current.pointerType === 'mouse' ? 4 : 10;
      if (Math.hypot(point.x - current.start.x, point.y - current.start.y) < slop) {
        return; // still a tap
      }
      current.moved = true;
      current.last = current.start; // catch up the slop at once
    }
    const dx = point.x - current.last.x;
    const dy = point.y - current.last.y;
    current.last = point;
    if (current.onView) {
      props.onDragView(dx, dy, overview.tile);
    } else {
      move(-dx / overview.tile, -dy / overview.tile); // the map follows the finger
    }
  }

  function onPointerEnd(event: PointerEvent<HTMLCanvasElement>, completed: boolean) {
    const current = drag.current;
    if (current === null || current.pointerId !== event.pointerId) {
      return;
    }
    drag.current = null;
    if (completed && !current.moved && overview !== null && size !== null) {
      props.onPick(toMap(overview, size, position(event)));
    } else if (current.onView) {
      followCamera({ x: camera.x, y: camera.y }); // the rectangle may have been dragged off the overview
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLCanvasElement>) {
    const step = OVERVIEW_GRID * (event.shiftKey ? 5 : 1);
    switch (event.key) {
      case 'ArrowLeft':
        move(-step, 0);
        break;
      case 'ArrowRight':
        move(step, 0);
        break;
      case 'ArrowUp':
        move(0, -step);
        break;
      case 'ArrowDown':
        move(0, step);
        break;
      case 'Enter':
      case ' ':
        if (overview !== null) {
          props.onPick({ x: overview.x, y: overview.y }); // the detailed map goes to the overview's middle
        }
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  // the legend names what the overview shows, not the whole chunks around it
  const guildsShown =
    layer === 'guild' && range !== undefined
      ? guildsInView(
          map.villages.filter(
            (village) =>
              village.x >= range.minX && village.x <= range.maxX && village.y >= range.minY && village.y <= range.maxY,
          ),
        )
      : [];
  const guilds = guildsShown.slice(0, LEGEND_GUILDS);
  const guildsLeft = guildsShown.length - guilds.length;

  return (
    <div className={variant === 'fill' ? `${styles.overview} ${styles.overviewFill}` : styles.overview}>
      {guildsEnabled && (
        <div className={styles.overviewLayer}>
          <SegmentedControl
            label={t.map.layer.label}
            options={[
              { value: 'owner', label: t.map.layer.owner },
              { value: 'guild', label: t.map.layer.guild },
            ]}
            value={layer}
            onChange={(next) => {
              setChosenLayer(next);
              saveMapLayer(next);
            }}
          />
        </div>
      )}
      <div className={styles.overviewFit}>
        <div ref={boxRef} className={styles.overviewBox}>
          <canvas
            ref={canvasRef}
            className={styles.overviewCanvas}
            tabIndex={0}
            role="application"
            aria-label={t.map.overviewLabel}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={(event) => onPointerEnd(event, true)}
            onPointerCancel={(event) => onPointerEnd(event, false)}
            onKeyDown={onKeyDown}
          />
          {map.error !== null && (
            <div className={styles.overviewStatus} role="status">
              {t.map.loadError}
              <button type="button" className={styles.retry} onClick={map.retry}>
                {t.common.retry}
              </button>
            </div>
          )}
        </div>
      </div>
      {layer === 'guild' && (
        <ul className={styles.guildLegend} aria-label={t.map.layer.legend}>
          {guilds.map((guild) => (
            <li key={guild.guildId}>
              <Link to={guildPath(guild.guildId)} className={styles.guildChip}>
                <span
                  className={styles.guildDot}
                  style={{ background: guildColor(guild.guildId, theme) }}
                  aria-hidden="true"
                />
                [{guild.tag}] <span className={styles.guildCount}>{guild.villages}</span>
              </Link>
            </li>
          ))}
          {guildsLeft > 0 && <li className={styles.guildNone}>{t.map.layer.moreGuilds(guildsLeft)}</li>}
          {guilds.length === 0 && <li className={styles.guildNone}>{t.map.layer.noGuilds}</li>}
        </ul>
      )}
      {/* nothing to move while the overview shows the whole map (a 100 × 100 world) */}
      {movable && (
        <div className={styles.overviewControls}>
          <IconButton
            icon="chevronLeft"
            label={t.map.overviewMove.left}
            onClick={() => move(-OVERVIEW_STEP, 0)}
            disabled={!canMove(-OVERVIEW_STEP, 0)}
          />
          <IconButton
            icon="chevronUp"
            label={t.map.overviewMove.up}
            onClick={() => move(0, -OVERVIEW_STEP)}
            disabled={!canMove(0, -OVERVIEW_STEP)}
          />
          <IconButton
            icon="chevronDown"
            label={t.map.overviewMove.down}
            onClick={() => move(0, OVERVIEW_STEP)}
            disabled={!canMove(0, OVERVIEW_STEP)}
          />
          <IconButton
            icon="chevronRight"
            label={t.map.overviewMove.right}
            onClick={() => move(OVERVIEW_STEP, 0)}
            disabled={!canMove(OVERVIEW_STEP, 0)}
          />
          <IconButton
            icon="locate"
            label={t.map.overviewHome}
            onClick={() => onCentreChange({ x: currentVillage.x + 0.5, y: currentVillage.y + 0.5 })}
          />
        </div>
      )}
    </div>
  );
}

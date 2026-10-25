import { useState } from 'react';
import { useRecruitment } from '../../api/queries/recruitment';
import { useTroops } from '../../api/queries/troops';
import { useVillage } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { goalForLosses } from '../../game/recruitTemplates';
import { presentUnits, unitsAway, type UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import { loadLossesGoal, saveLossesGoal } from './recruitLossesStorage';
import { PlanSheet } from './RecruitTemplates';

interface RecruitLossesButtonProps {
  /** The report: the goal that was recruited is remembered per report and village. */
  reportId: number;
  villageId: number;
  /** What the battle cost this village. */
  losses: UnitMap;
  /** The village's name, when the report offers more than one village (the button then names it). */
  villageName?: string;
}

/**
 * "Re-recruit losses" on a battle report (1.6.3): opens the recruit plan sheet with the units the
 * battle cost the village as the goal on top of what it owns now, so one tap queues them as far as
 * the resources go (every unit cut by the same share). The goals can be changed first. It is no
 * template; only a goal that was recruited is remembered, so opening the report again does not ask
 * for the same losses twice.
 */
export function RecruitLossesButton({ reportId, villageId, losses, villageName }: RecruitLossesButtonProps) {
  const t = useT();
  // the village's data is asked for when the button is first tapped, not for every report read
  const [asked, setAsked] = useState(false);
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="small"
        aria-haspopup="dialog"
        onClick={() => {
          setAsked(true);
          setOpen(true);
        }}
      >
        {villageName === undefined ? t.recruitLosses.button : t.recruitLosses.buttonFor(villageName)}
      </Button>
      {asked && (
        <LossesPlan
          reportId={reportId}
          villageId={villageId}
          losses={losses}
          open={open}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}

interface LossesPlanProps {
  reportId: number;
  villageId: number;
  losses: UnitMap;
  open: boolean;
  onClose: () => void;
}

/** Loads the village the way the recruit page does, then shows the plan of its losses. */
function LossesPlan({ reportId, villageId, losses, open, onClose }: LossesPlanProps) {
  const t = useT();
  const village = useVillage(villageId);
  const recruitment = useRecruitment(villageId);
  const troops = useTroops(villageId);
  const world = useWorld();
  // set once everything is there and kept: from then on the goals are the player's to change
  const [goal, setGoal] = useState<UnitMap>();
  const away = troops.data === undefined ? undefined : unitsAway(troops.data);
  if (goal === undefined && village.data !== undefined && recruitment.data !== undefined && away !== undefined) {
    // a plan recruited from this report before keeps its goal: the queued units are no new loss
    setGoal(
      loadLossesGoal(reportId, villageId) ?? goalForLosses(losses, recruitment.data, village.data.unitsHome, away),
    );
  }
  if (village.data === undefined || recruitment.data === undefined || goal === undefined) {
    const failed = village.isError || recruitment.isError || troops.isError;
    return (
      <Sheet open={open} onClose={onClose} title={t.recruitLosses.title}>
        {failed ? <ErrorMessage>{t.recruitLosses.failed}</ErrorMessage> : <p>{t.common.loading}</p>}
      </Sheet>
    );
  }
  return (
    <PlanSheet
      village={village.data}
      buildings={recruitment.data}
      away={away}
      queueSize={world.data?.recruitQueueSize}
      // the letter and name are no template's: the title and hint below stand for them
      template={open ? { letter: 'A', name: '', units: goal } : undefined}
      title={t.recruitLosses.title}
      hint={t.recruitLosses.hint}
      goalEdit={{
        units: presentUnits(losses).map(([type]) => type),
        onChange: (unit, value) => setGoal((current) => ({ ...current, [unit]: value })),
      }}
      onRecruited={() => saveLossesGoal(reportId, villageId, goal)}
      onClose={onClose}
    />
  );
}

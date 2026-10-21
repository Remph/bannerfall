import type { ReactNode } from 'react';
import type { BuildingInfo } from '../../api/queries/buildings';
import { InfoRow } from '../../components/InfoRow';
import { useT } from '../../i18n';
import { UpgradePreviewBody, type PreviewContext } from './UpgradePreview';

interface RowProps {
  info: BuildingInfo;
  context: PreviewContext;
  /** The row's class names (highlight, locked). */
  className: string | undefined;
  /** The row's content; gets the info button to put into the row head. */
  children: (infoButton: ReactNode) => ReactNode;
}

/** A building row of the Town Hall with the upgrade preview (M24) behind its info button. */
export function PreviewRow({ info, context, className, children }: RowProps) {
  const t = useT();
  const building = t.buildings[info.building];
  return (
    <InfoRow
      id={info.building}
      title={building}
      label={t.townHall.preview.button(building)}
      className={className}
      body={<UpgradePreviewBody info={info} context={context} />}
    >
      {children}
    </InfoRow>
  );
}

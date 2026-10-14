/** `/v/1/warehouse`: the warehouse page (M22), reached from the village overview and the resource bar (M35). */
export function warehousePath(villageId: number): string {
  return `/v/${villageId}/warehouse`;
}

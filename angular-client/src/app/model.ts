export class Position {
  quantity: number = -1;
}

export class Fundamental {
  low: number = -.1;
  high: number = -.1;
  open: number = -.1;
}

export interface Order {
  created_at: string;
  id: string;
  cumulative_quantity: number;
  state: string;
  price: number;
  average_price: number;
  side: string;
  matchId: string;
}

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
  quantity: number;
  state: string;
  price: number;
  average_price: number;
  side: string;
  matchId: string;  // a concatenation of the id's of 2 matched orders, is very long
  mId: string;      // a shorter representation of matchId for display only
}

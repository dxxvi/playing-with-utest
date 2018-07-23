import {Component, ElementRef, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';
import * as Highcharts from 'highcharts/highstock';

@Component({
  selector: 'div.configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  open = false;
  message: string = null;
  symbol: string = null;
  compound: {
    symbol: string,
    fundamental: {
      open: number,
      low: number,
      high: number,
      high_52_weeks: number,
      low_52_weeks: number,
      dividend_yield: string,
      description: string,
      year_found: number
    },
    quotes: Array<{
      beginsAt: string,
      closePrice: number,
      highPrice: number,
      interpolated: boolean,
      lowPrice: number,
      openPrice: number,
      session: string,
      volume: number
    }>
  } = null;
  private subscription: Subscription;
  private hc: any;
  private cdiv: any;

  constructor(private el: ElementRef, private websocketService: WebsocketService) { }

  ngOnInit() {
    this.hc = this.el.nativeElement.querySelector('div.highcharts');
    this.cdiv = this.el.nativeElement.querySelector('div.c');

    this.subscription = this.websocketService.getSubject('FUNDAMENTAL_REVIEW').asObservable().subscribe(message => {
      const i = message.text.indexOf(': ');
      if (i == -1) {
        console.error(`Unknown message ${message.text}`);
      }
      else {
        this.compound = JSON.parse(message.text.substr(i + 2));
        this.compound.symbol = message.text.substring(0, i);
        this.hc.innerHTML = '';
        this.hc.removeAttribute('style');
        if (this.compound != null && this.compound.quotes != null) {
          this.cdiv.style.width = document.body.clientWidth / 2 - 50 + 'px';
          setTimeout(() => {
            this.hc.style.width = this.cdiv.clientWidth - 20 + 'px';
            this.hc.style.height = this.cdiv.clientHeight + 'px';

            const colorIndex = 0;
            const highchartsData: Array<Array<number>> = this.compound.quotes.map(q => {
              const a = q.beginsAt.split(/[TZ:-]/);
              const m = Date.UTC(parseInt(a[0]), parseInt(a[1])-1, parseInt(a[2]), parseInt(a[3]), parseInt(a[4]));
              return [m, q.openPrice];
            });

            if (highchartsData.length === 0) {
              return;
            }

            const firstDate: Date = new Date(0);
            firstDate.setUTCMilliseconds(highchartsData[0][0]);
            const delta = 5 - firstDate.getUTCDay();
            const breaks: Array<{from: number, to: number, repeat: number}> = [{
              from: Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth(), firstDate.getUTCDate(), 19, 55),
              to: Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth(), firstDate.getUTCDate() + 1, 13, 30),
              repeat: 24 * 36e5
            }, {
              from: Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth(), firstDate.getUTCDate() + delta, 19, 55),
              to: Date.UTC(firstDate.getUTCFullYear(), firstDate.getUTCMonth(), firstDate.getUTCDate() + delta + 2, 13, 30),
              repeat: 7 * 24 * 36e5
            }];
            console.dir(breaks);

            const chartOptions: any = {
              credits: { enabled: false },
              rangeSelector: { enabled: false },
              scrollbar: { enabled: false },
              title: {},
              xAxis: {
                breaks: breaks
              },
              series: [{
                name: null,
                type: 'area',
                gapSize: 3,
                lineWidth: 1,
                threshold: null,
                fillColor: {
                  linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                  stops: [
                    [0, Highcharts.getOptions().colors[colorIndex]],
                    [1, Highcharts.Color(Highcharts.getOptions().colors[colorIndex]).setOpacity(0).get('rgba')]
                  ]
                },
                data:highchartsData
              }]
            };
            Highcharts.stockChart(this.hc, chartOptions);
          }, 1904);  // wait a bit for the cdiv having clientWidth and clientHeight
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  addToWatchList(symbol: string) {
    if (symbol != null && symbol.trim() != '') {
      this.message = `WATCHLIST_ADD: ${symbol}`;
      this.sendMessageToServer();
    }
  }

  clickConfiguration() {
    if (this.open) {
      this.removeFundamentalReviewSymbol();
    }
    this.open = !this.open;
    this.el.nativeElement.style.position = this.open ? 'static' : 'fixed';
    if (this.open) {
      setTimeout(() => {
        this.el.nativeElement.scrollIntoView({ behavior: 'smooth' });
      }, 419);
    }
  }

  getFundamental() {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `FUNDAMENTAL_REVIEW: ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }

  removeFundamentalReviewSymbol() {
    this.compound = null;
    this.cdiv.style.width = 'auto';
    this.hc.innerHTML = '';
    this.hc.removeAttribute('style');
  }

  sendMessageToBrowser() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.processReceivedString(this.message);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  sendMessageToServer() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.sendMessageThroughWebsocket(this.message);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  setDebug(debug: boolean) {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `${debug ? 'DEBUG_ON:' : 'DEBUG_OFF:'} ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }
}

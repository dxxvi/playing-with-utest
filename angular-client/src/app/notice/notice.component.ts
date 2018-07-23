import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'div.notice',
  templateUrl: './notice.component.html',
  styleUrls: ['./notice.component.css']
})
export class NoticeComponent implements OnInit {
  @Input() uuid: string;
  @Input() message: string;
  @Input() level: string;

  constructor() { }

  ngOnInit() {
  }

}

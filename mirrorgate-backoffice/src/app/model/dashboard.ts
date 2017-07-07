/*
 * Copyright 2017 Banco Bilbao Vizcaya Argentaria, S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export class Dashboard {
  name: string;
  displayName: string;
  logoUrl: string;
  programIncrement: string;
  applications: string[] = [];
  codeRepos: string[] = [];
  boards: string[] = [];
  adminUsers: string[] = [];
  filter: {
    timeSpan: number,
    branch: Map<string,boolean>,
    status: Map<string,boolean>
  }
  slackTeam: string;
  slackToken: string;
  urlAlerts: string;
  slackChannel: string;
  lastUserEdit: string;
  lastModification: number;
}

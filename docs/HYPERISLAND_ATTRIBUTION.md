# HyperIsland donor attribution

The root/libxposed migration adapts selected infrastructure patterns from
[HyperIsland](https://github.com/1812z/HyperIsland), using the bundled donor
checkout in `docs/HyperIsland-main` as the compatibility reference.

Adapted areas include modern libxposed module/service setup, scope requests,
plugin classloader observation, Xiaomi SystemUI Focus hooks, XMSF Focus
authorization, and root-managed process restarts. HyperBridge retains its own
notification parsers, payloads, visuals, actions, and lifecycle model.

HyperIsland is distributed under the MIT License:

Copyright (c) 2026 1812z

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

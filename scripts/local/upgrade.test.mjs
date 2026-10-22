import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync, existsSync, mkdirSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const script = join(root, 'scripts/local/upgrade.sh')
const artifacts = join(root, '.codebuddy/artifacts')

function fixture(t) {
  mkdirSync(artifacts, { recursive: true })
  const directory = mkdtempSync(join(artifacts, 'upgrade-test-'))
  t.after(() => rmSync(directory, { recursive: true, force: true }))
  const calls = join(directory, 'docker.calls')
  const bin = join(directory, 'bin')
  mkdirSync(bin)
  writeFileSync(join(bin, 'docker'), '#!/bin/sh\nprintf "%s\\n" "$*" >> "$UPGRADE_TEST_CALLS"\ncat >/dev/null\nexit "${UPGRADE_TEST_DOCKER_EXIT:-0}"\n', { mode: 0o755 })
  const workspace = join(directory, 'workspace')
  const local = join(workspace, 'scripts/local')
  mkdirSync(local, { recursive: true })
  const runner = join(local, 'upgrade.sh')
  if (existsSync(script)) writeFileSync(runner, readFileSync(script))
  for (const [module, file] of [
    ['zhixue-course', '2026-10-20-java-courses.sql'],
    ['zhixue-ai', '2026-10-22-ai-reliability.sql'],
    ['zhixue-ai', '2026-10-20-java-knowledge.sql'],
    ['zhixue-ai', '2026-10-20-ai-conversation.sql']
  ]) {
    const sql = join(workspace, 'zhixue-modules', module, 'src/main/resources/sql/upgrade', file)
    mkdirSync(dirname(sql), { recursive: true })
    writeFileSync(sql, 'SELECT 1;\n')
  }
  const config = join(workspace, 'ops/local/local.env')
  mkdirSync(dirname(config), { recursive: true })
  writeFileSync(config, '')
  return { directory, calls, config, runner, env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, UPGRADE_TEST_CALLS: calls } }
}

test('dry-run lists migrations in dependency order without reading config or calling Docker', t => {
  const { directory, calls, config, runner, env } = fixture(t)
  const marker = join(directory, 'config-was-sourced')
  writeFileSync(config, `touch '${marker}'\n`)
  const before = readFileSync(config, 'utf8')
  const result = spawnSync('bash', [runner, '--dry-run'], {
    cwd: directory, env, encoding: 'utf8', timeout: 5000
  })
  assert.equal(result.status, 0, result.stderr)
  assert.deepEqual(result.stdout.trim().split('\n'), [
    '[upgrade][dry-run] zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql',
    '[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql',
    '[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql',
    '[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-22-ai-reliability.sql',
    '[ok] 增量脚本预览完成（共 4 个）'
  ])
  assert.equal(existsSync(marker), false)
  assert.equal(existsSync(calls), false)
  assert.equal(readFileSync(config, 'utf8'), before)
})

test('unknown CLI arguments fail before config or Docker access', t => {
  const { directory, calls, runner, env } = fixture(t)
  const result = spawnSync('bash', [runner, '--dr-run'], {
    cwd: directory, env, encoding: 'utf8', timeout: 5000
  })
  assert.equal(result.status, 2)
  assert.match(result.stderr, /用法/)
  assert.equal(existsSync(calls), false)
})

test('explicit empty argument is rejected without invoking migrations', t => {
  const { directory, calls, runner, env } = fixture(t)
  const result = spawnSync('bash', [runner, ''], {
    cwd: directory, env, encoding: 'utf8', timeout: 5000
  })
  assert.equal(result.status, 2)
  assert.match(result.stderr, /用法/)
  assert.equal(existsSync(calls), false)
})

test('migration errors stop at the first SQL file without leaking host credentials', t => {
  const { directory, calls, runner, env } = fixture(t)
  const result = spawnSync('bash', [runner], {
    cwd: directory,
    env: { ...env, UPGRADE_TEST_DOCKER_EXIT: '23', ZHIXUE_DB_PASSWORD: 'host-credential-must-not-appear' },
    encoding: 'utf8', timeout: 5000
  })
  assert.equal(result.status, 23, result.stderr)
  assert.equal(readFileSync(calls, 'utf8').trim().split('\n').length, 1)
  assert.match(result.stdout, /2026-10-20-java-courses.sql/)
  assert.doesNotMatch(result.stdout, /2026-10-20-ai-conversation.sql|执行完成/)
  assert.doesNotMatch(readFileSync(calls, 'utf8') + result.stdout + result.stderr, /host-credential-must-not-appear/)
  assert.match(readFileSync(calls, 'utf8'), /MYSQL_ROOT_PASSWORD/)
})

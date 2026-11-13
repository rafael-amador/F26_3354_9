// UC-1 "View Daily Summary & Goals" — SD-1 "updated daily summary -> display calories, macros, water progress".
// FR-2 (calories), FR-6 (water, low-intake message), steps from FR-4 sync. NFR Safety warnings are listed too.
import React from 'react';
import { RefreshControl, ScrollView, Text } from 'react-native';
import { api } from '../api';
import { progress } from '../logic';
import { Banner, Card, colors, MaintenanceNotice, ProgressBar, Row, s, Stat } from '../ui';
import { useAsync } from '../useAsync';

export default function TodayScreen({ refreshKey }: { refreshKey: number }) {
  const { data, error, loading, reload } = useAsync(() => api.summary(), [refreshKey]);

  if (error?.isMaintenance) return <MaintenanceNotice name="Nutrition & Hydration" />;

  return (
    <ScrollView refreshControl={<RefreshControl refreshing={loading} onRefresh={reload} />}>
      {error ? <Banner kind="error">{error.message}</Banner> : null}
      {data ? (
        <>
          <Text style={[s.h1, { marginBottom: 10 }]}>Today ({data.date})</Text>
          {data.messages.map((m) => (
            <Banner key={m} kind={m.startsWith('Water goal reached') ? 'good' : 'warn'}>{m}</Banner>
          ))}
          <Card title="Calories">
            <Text style={s.body}>
              {data.calories} / {data.calorieGoal} kcal
            </Text>
            <ProgressBar value={progress(data.calories, data.calorieGoal)} color={data.calories > data.calorieGoal ? colors.warn : colors.primary} />
            <Row>
              <Stat label="Protein" value={`${data.proteinG} g`} />
              <Stat label="Carbs" value={`${data.carbsG} g`} />
              <Stat label="Fat" value={`${data.fatG} g`} />
              <Stat label="Meals" value={String(data.mealCount)} />
            </Row>
          </Card>
          <Card title="Water">
            <Text style={s.body}>
              {data.waterMl} / {data.waterGoalMl} ml
            </Text>
            <ProgressBar value={progress(data.waterMl, data.waterGoalMl)} color="#2A8BD8" />
            <Text style={s.muted}>
              That's {(data.waterMl / data.bottleSizeMl).toFixed(1)} bottles of {data.bottleSizeMl} ml.
            </Text>
          </Card>
          <Card title="Steps">
            <Text style={s.body}>
              {data.steps} / {data.stepGoal} steps
            </Text>
            <ProgressBar value={progress(data.steps, data.stepGoal)} color={colors.good} />
            <Text style={s.muted}>
              {data.latestHeartRate ? `Latest heart rate: ${data.latestHeartRate} bpm` : 'Connect a smart device in Activity to sync steps.'}
            </Text>
          </Card>
        </>
      ) : null}
    </ScrollView>
  );
}

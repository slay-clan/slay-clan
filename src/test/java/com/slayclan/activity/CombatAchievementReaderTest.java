package com.slayclan.activity;

import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CombatAchievementReaderTest
{
	private Client client;
	private CombatAchievementReader reader;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		reader = new CombatAchievementReader(client);
	}

	@Test
	public void sumsPointsFromTierCounts()
	{
		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY)).thenReturn(41);
		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM)).thenReturn(38);
		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD)).thenReturn(22);
		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE)).thenReturn(9);
		when(client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.CA_TIER_STATUS_HARD)).thenReturn(2);

		CombatAchievementStatus status = reader.readStatus();

		assertEquals(41 + 76 + 66 + 36 + 15, status.points());
		assertEquals("41/38/22/9/3/0", status.completedText());
		assertEquals("0/0/2/0/0/0", status.tierStatusText());
	}

	@Test
	public void listsCompletedTasksWithNamesFromTheCache()
	{
		EnumComposition easy = mock(EnumComposition.class);
		when(easy.getIntVals()).thenReturn(new int[]{500, 501});
		EnumComposition empty = mock(EnumComposition.class);
		when(empty.getIntVals()).thenReturn(new int[0]);
		when(client.getEnum(anyInt())).thenReturn(empty);
		when(client.getEnum(CombatAchievementReader.TIER_ENUMS[0])).thenReturn(easy);
		StructComposition den = mock(StructComposition.class);
		when(den.getIntValue(CombatAchievementReader.PARAM_ID)).thenReturn(398);
		when(den.getStringValue(CombatAchievementReader.PARAM_NAME)).thenReturn("Into the Den of Giants");
		StructComposition other = mock(StructComposition.class);
		when(other.getIntValue(CombatAchievementReader.PARAM_ID)).thenReturn(33);
		when(other.getStringValue(CombatAchievementReader.PARAM_NAME)).thenReturn("Not Done");
		when(client.getStructComposition(500)).thenReturn(den);
		when(client.getStructComposition(501)).thenReturn(other);
		when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_12)).thenReturn(1 << (398 % 32));

		Optional<List<CompletedAchievement>> completed = reader.readCompleted();

		assertEquals(Optional.of(List.of(new CompletedAchievement(398, "Into the Den of Giants", "Easy"))), completed);
	}

	@Test
	public void hasNoTaskListWhileTheCacheHasNotLoadedTheTierEnums()
	{
		when(client.getEnum(anyInt())).thenReturn(null);

		assertFalse(reader.readCompleted().isPresent());
	}

	@Test
	public void knowsWhichVarbitsCarryCombatAchievementProgress()
	{
		assertTrue(CombatAchievementReader.readsVarbit(VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER));
		assertTrue(CombatAchievementReader.readsVarbit(VarbitID.CA_TIER_STATUS_EASY));
		assertFalse(CombatAchievementReader.readsVarbit(VarbitID.CHAT_FILTER_ASSISTSYSTEM));
	}
}

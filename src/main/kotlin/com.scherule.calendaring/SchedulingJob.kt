package com.scherule.calendaring

import com.scherule.calendaring.domain.MeetingParameters
import com.scherule.calendaring.domain.Participant
import com.scherule.calendaring.domain.SchedulingJobId

class SchedulingJob(
        val id: SchedulingJobId,
        val parameters: MeetingParameters,
        val participants: Set<Participant>
)
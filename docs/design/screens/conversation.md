# Conversation

The conversation screen is text-only: local alias/approved peer fallback in the top bar, a safety/identity notice only when relevant, message body, minimal timestamps, and text composer. Overflow contains Verify identity, Local name, Block, Reset secure relationship, and Destroy conversation.

There are no avatars/profile route, phone/video controls, microphone, camera, gallery, attachments, stickers, reactions, GIFs, location, contact card, typing, online state, last seen, seen, delivered, or read UI. The composer has no hidden attachment affordance.

## Message presentation

Use restrained own/peer alignment or surface distinction, readable line width, and modest bubble grouping; do not use loud colors. Timestamps appear once per nearby message group or on tap/long press, rather than beside every item. They are local metadata, not a peer activity feed.

## Sending status

V1 shows **Sending** only while an active outbound operation is in progress. After relay acceptance it removes the status instead of displaying persistent **Sent**. This is the least ambiguous treatment: it provides local feedback without implying delivery. Do not use checkmarks or show Delivered, Read, Seen, or recipient-client ACK semantics. Failure is a local generic send outcome and follows message lifecycle policy.

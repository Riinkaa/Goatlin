const express = require('express');
const auth = require('../middleware/auth');
const Account = require('../models/account');
const Note = require('../models/note');

const router = express.Router();

// Checks that URL values are plain strings with safe characters only
function getSafeParam(value) {
    if (typeof value !== 'string' || !/^[a-zA-Z0-9@._-]+$/.test(value)) {
        return null;
    }
    return value;
}

// Keeps only plain values and blocks MongoDB '$' operators or nested objects
function sanitizeBody(body) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
        return {};
    }

    const clean = {};
    for (const [key, value] of Object.entries(body)) {
        if (key.startsWith('$') || key.includes('.')) {
            continue;
        }
        if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
            clean[key] = value;
        }
    }
    return clean;
}

router.post('/accounts', async (req, res, next) => {
    try {
        const cleanAccountData = sanitizeBody(req.body);
        const account = new Account(cleanAccountData);
        await account.save();

        res.status(201);
    } catch (e) {
        let status = 404;
        let error = 'Failed to create account';

        if (e.name === 'MongoError' && e.code === 11000) {
            status = 409;
            error = 'Email address is already registered';
        }

        res.status(status).json({ error });
    } finally {
        res.end();
    }
});

router.put('/accounts/:username/notes/:note', auth, async (req, res, next) => {
    const username = getSafeParam(req.params.username);
    const noteId = getSafeParam(req.params.note);

    if (!username || !noteId) {
        return res.status(400).json({ error: 'Invalid username or note ID' });
    }

    const cleanNoteBody = sanitizeBody(req.body);
    delete cleanNoteBody.id;
    delete cleanNoteBody.owner;

    const safeNote = {
        ...cleanNoteBody,
        id: noteId,
        owner: username
    };

    try {
        const note = await Note.findOneAndUpdate(
            { owner: username, id: noteId },
            { $set: safeNote },
            { new: true, upsert: true }
        );

        res.status(204);
    } catch (e) {
        let status = 500;
        let error = 'Failed to create/update note';

        console.log(e);

        res.status(status).json({ error });
    } finally {
        res.end();
    }
});

router.get('/accounts/:username/notes', auth, async (req, res, next) => {
    const username = getSafeParam(req.params.username);

    if (!username) {
        return res.status(400).json({ error: 'Invalid username' });
    }

    try {
        const notes = await Note.find(
            { owner: username },
            null,
            { lean: true }
        ).exec();

        res.status(200).json(notes).end();
    } catch (e) {
        let status = 500;
        let error = e.message;

        console.error(e);

        res.status(status).json({ error });
    } finally {
        res.end();
    }
});

module.exports = router;
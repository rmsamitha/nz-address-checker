/*
 * Copyright 2025 Samitha Chathuranga
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

import React from 'react';
import { Box, Typography, TextField, CircularProgress, Autocomplete } from '@mui/material';
import { fetchAddressSuggestions } from './api';
import debounce from 'lodash.debounce';
import { useMemo } from 'react';
import Paper from '@mui/material/Paper';
import bgImage from './assets/bg-image.jpg';
import HomeIcon from '@mui/icons-material/Home';
import IconButton from '@mui/material/IconButton';

function App() {
  const [query, setQuery] = React.useState('');
  const [suggestions, setSuggestions] = React.useState([]);
  const [loading, setLoading] = React.useState(false);

  const debouncedGetSuggestions = useMemo(() =>
    debounce(async (input) => {
      if (!input) {
        setSuggestions([]);
        return;
      }
      setLoading(true);
      try {
        const response = await fetchAddressSuggestions(input);
        const raw = response.data?.addresses || [];
        const addresses = raw.map(item => item.FullAddress);
        setSuggestions(addresses);
      } catch (err) {
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, 400)
    , []);


  const handleInputChange = (event, value) => {
    setQuery(value);
    if (value.length >= 3) {
      debouncedGetSuggestions(value);
    } else {
      setSuggestions([]);
    }
  };

  return (
    <Box sx={{ height: "100vh", width: "100vw" }}>
      <Box
        sx={{
          height: "7em",
          bgcolor: "#f9f4f0ff",
          display: 'flex',
          flexDirection: 'row',
          justifyContent: "space-between", // Places items at left & right
          pr: 3,
          pl: 3
        }}
      >
        <Typography fontSize='1.5em' gutterBottom align="left" color='#5f5f60ff' sx={{ pl: 4, pt: 2.2, mb: 6 }}>
          NEW ZEALAND<br />
          ADDRESS CHECKER
        </Typography>
        <IconButton>
          <HomeIcon sx={{ fontSize: 40 }} />
        </IconButton>
      </Box>

      {/* Area-2 with Background Image */}
      <Box
        sx={{
          height: "70%",
          backgroundImage: `url(${bgImage})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
        }}
      >

        {/* Area-3 (Beige Box for AutoComplete) */}
        <Paper
          sx={{
            height: "40%",
            bgcolor: "#f9f0f0ff",
            pt: "1em",
            pb: "4em",
            pl: "4em",
            pr: "4em",
            borderRadius: 3,
            boxShadow: 3,
            width: "30%",
            minWidth: 400,
          }}
        >


          <Typography fontSize='0.9em' gutterBottom align="center" color='#124227ff' sx={{ mb: 6, mt: 3 }}>
            TYPE AT LEAST 3 CHARACTERS TO SEARCH FOR <br />
            A NEW ZEALAND POSTAL ADDRESS
          </Typography>

          <Autocomplete
            freeSolo
            filterOptions={(x) => x}
            options={suggestions}
            getOptionLabel={(option) => option}
            inputValue={query}
            onInputChange={handleInputChange}
            loading={loading}
            sx={{ bp: 4, lp: 20, rp: 4, mp: 2 }}

            renderInput={(params) => (
              <TextField
                {...params}
                label="Enter NZ Postal Address"
                variant="outlined"
                fullWidth
                autoComplete="off"
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {loading && <CircularProgress size={20} />}
                      {params.InputProps.endAdornment}
                    </>
                  )
                }}
              />
            )}
          />

        </Paper>
      </Box>
      <Box sx={{ bgcolor: "#f9f4f0ff", pr: 4, pt: 2, pb: 1 }}>
        <Typography fontSize='1em' gutterBottom align="right" color='#124227ff' sx={{}}>
          Developed by Samitha Chathuranga
        </Typography>
      </Box>
    </Box>
  );
}

export default App;
